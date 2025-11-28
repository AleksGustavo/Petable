<?php
// Arquivo: servicos_agendar_banhotosa.php

// Inclui o arquivo de conexão e verifica a sessão, se necessário (boa prática)
require_once 'conexao.php';

// --- 1. Mapeamento de IDs e Busca de Dados Necessários ---

// IDs Fictícios/Assumidos para categorização. AJUSTE ESTES IDs conforme seu DB.
const BANHO_TOSA_IDS = [1, 2, 3]; // Banho, Tosa Higiênica, Tosa Completa
const CONSULTAS_IDS = [20, 21]; // Ex: Consulta Geral, Retorno

// ====================================================================
// ✅ CORREÇÃO AQUI: Alterado de 10 para 4 (ID de 'Consulta Veterinária' na tabela `servico`)
const VACINACAO_ID = 4;
// ====================================================================

// Mapeia TODOS os serviços (Banho/Tosa e Consultas) para o mapa principal
// Adiciona o ID genérico de Vacinação para que o nome e preço (se houver) sejam carregados
$all_servico_ids = array_merge(BANHO_TOSA_IDS, CONSULTAS_IDS, [VACINACAO_ID]);
$servicos_map = []; // Contém todos os serviços (nomes e preços por porte)

// A. Buscar Serviços (Banho/Tosa e Consultas)
// Assumindo que você tem uma tabela chamada 'servico' com as colunas 'id' e 'nome'.
if (!empty($all_servico_ids)) {
    $sql_servicos = "SELECT id, nome FROM servico WHERE id IN (" . implode(', ', $all_servico_ids) . ")";
    $result_servicos = mysqli_query($conexao, $sql_servicos);
    $servicos_lista = mysqli_fetch_all($result_servicos, MYSQLI_ASSOC);
    
    // Mapeia serviços para uso no JS
    foreach ($servicos_lista as $s) {
        $servicos_map[$s['id']] = ['nome' => $s['nome'], 'id' => $s['id']];
    }
} else {
    $servicos_lista = [];
}


// B. Buscar Preços por Porte (Apenas para Banho e Tosa)
$banho_tosa_ids_str = implode(', ', BANHO_TOSA_IDS);
// A tabela deve ser `preco_servico_porte`
// LINHA CORRIGIDA (26)
$sql_precos_porte = "SELECT servico_id, porte, preco
                     FROM preco_servico_porte
                     WHERE servico_id IN ({$banho_tosa_ids_str})";
$result_precos_porte = mysqli_query($conexao, $sql_precos_porte);

if ($result_precos_porte) {
    while ($row = mysqli_fetch_assoc($result_precos_porte)) {
        // Adiciona o preço do porte ao mapa do serviço correspondente (Apenas Banho/Tosa)
        if (isset($servicos_map[$row['servico_id']])) {
            $servicos_map[$row['servico_id']][$row['porte']] = (float)$row['preco'];
        }
    }
}


// C. Buscar Funcionários (Groomers e Veterinários)
$sql_funcionarios = "SELECT f.id, f.nome
                     FROM funcionario f
                     JOIN usuario u ON f.usuario_id = u.id
                     WHERE u.ativo = 1
                     ORDER BY f.nome ASC";
$result_funcionarios = mysqli_query($conexao, $sql_funcionarios);
$funcionarios = mysqli_fetch_all($result_funcionarios, MYSQLI_ASSOC);

// --- Separação da Lista de Serviços para os Selects (HTML) ---

// Filtra a lista principal de serviços para consultas
$consultas_select = array_filter($servicos_lista, function($s) {
    return in_array($s['id'], CONSULTAS_IDS);
});

// Busca a lista de vacinas ativas (IDs da tabela `vacina`)
$sql_vacinas = "SELECT id, nome FROM vacina WHERE ativo = 1 ORDER BY nome ASC";
$result_vacinas = mysqli_query($conexao, $sql_vacinas);
$vacinas_select = mysqli_fetch_all($result_vacinas, MYSQLI_ASSOC);

mysqli_close($conexao);

// A lista de funcionários para o Select do Veterinário
$veterinarios_select = $funcionarios;
?>

<h4 class="mb-3"><i class="fas fa-calendar-check me-2"></i> Agendar Serviços</h4>

<div id="status-message-area"></div>

<div class="card p-0 shadow-sm main-compact-card">
    <div class="card-body">
        <form id="form-agendar-servico" action="servicos_processar_agendamento.php" method="POST" novalidate>

            <input type="hidden" id="cliente_id_hidden" name="cliente_id" value="">
            <input type="hidden" id="pet_id_hidden" name="pet_id" value="">
            <input type="hidden" id="pet_porte_hidden" name="pet_porte" value="">
            <input type="hidden" id="tipo_servico_principal" name="tipo_servico_principal" value="">
            <input type="hidden" id="servicos_agendados_json" name="servicos_agendados_json" value="">
            <input type="hidden" id="vacina_real_id_hidden" name="vacina_real_id" value=""> <div id="step-cliente" class="mb-3">
                <label for="search_cliente" class="form-label">Buscar Cliente *</label>
                <input type="text" class="form-control form-control-sm" id="search_cliente" placeholder="Digite o nome ou CPF do Cliente">
                <div id="search-cliente-results" class="list-group mt-1">
                </div>
                <div class="form-text mt-2 text-muted" id="selected-cliente-info" style="display: none;">
                    Cliente Selecionado: <strong></strong>
                </div>
            </div>

            <div id="step-pet" class="mb-3" style="display: none;">
                <label for="pet_id_select" class="form-label">Pet do Cliente Selecionado *</label>
                <select class="form-select form-select-sm" id="pet_id_select" disabled>
                    <option value="">Carregando Pets...</option>
                </select>
                <div class="form-text mt-2 text-muted" id="selected-pet-info" style="display: none;">
                    Pet Selecionado: <strong></strong>
                </div>
                <div class="mt-2" id="pet_porte_info" style="display: none;">
                    Porte: <strong id="pet_porte_display"></strong>
                </div>
            </div>

            <div id="step-agendamento" style="display: none;">
                <hr class="mt-3 mb-2">
                <h5 class="mb-2">Configuração do Serviço</h5>

                <div class="mb-3">
                    <label for="servico_categoria" class="form-label">Categoria de Serviço *</label>
                    <select class="form-select form-select-sm" id="servico_categoria" required>
                        <option value="">Selecione a Categoria</option>
                        <option value="banho_tosa">Banho e Tosa</option>
                        <option value="vacina">Vacinação</option>
                        <option value="consulta">Consulta Veterinária</option>
                    </select>
                </div>
                
                <div id="servico-campos-especificos">
                    
                    <div id="campos-banho-tosa" style="display: none;">
                        <div class="mb-2">
                            <label for="servico_tipo_banho_tosa" class="form-label">Serviço de Banho/Tosa *</label>
                            <select class="form-select form-select-sm" id="servico_tipo_banho_tosa">
                                <option value="">Selecione o Serviço Principal</option>
                                <option value="banho" data-servico-id="1">Banho</option>
                                <option value="tosa">Tosa</option>
                                <option value="banho_e_tosa">Banho e Tosa</option>
                            </select>
                        </div>

                        <div id="tosa-options" class="mb-2" style="display: none;">
                            <label for="tosa_tipo" class="form-label">Tipo de Tosa *</label>
                            <select class="form-select form-select-sm" id="tosa_tipo">
                                <option value="">Selecione o Tipo de Tosa</option>
                                <option value="higiencia" data-servico-id="2">Tosa Higiênica</option>
                                <option value="completa" data-servico-id="3">Tosa Completa</option>
                            </select>
                        </div>
                        
                        <div class="mb-2">
                            <label for="funcionario_id_banhotosa" class="form-label">Funcionário Responsável (Opcional)</label>
                            <select class="form-select form-select-sm" id="funcionario_id_banhotosa" name="funcionario_id_banhotosa">
                                <option value="">Nenhum Funcionário Atribuído</option>
                                <?php foreach ($funcionarios as $funcionario): ?>
                                    <option value="<?php echo $funcionario['id']; ?>"><?php echo $funcionario['nome']; ?></option>
                                <?php endforeach; ?>
                            </select>
                        </div>
                    </div>
                    <div id="campos-vacina" style="display: none;">
                        <div class="mb-2">
                            <label for="vacina_servico_id" class="form-label">Vacina a ser Aplicada *</label>
                            <select class="form-select form-select-sm" id="vacina_servico_id">
                                <option value="">Selecione a Vacina</option>
                                <?php foreach ($vacinas_select as $vacina): ?>
                                    <option value="<?php echo $vacina['id']; ?>"><?php echo $vacina['nome']; ?></option>
                                <?php endforeach; ?>
                            </select>
                            <div class="form-text text-muted mb-1">A aplicação de vacina não gera custo imediato no agendamento.</div>
                        </div>

                        <div class="row">
                            <div class="col-md-6 mb-2">
                                <label for="vacina_retorno_previsto" class="form-label">Retorno Previsto (Validade) *</label>
                                <input type="date" class="form-control form-control-sm" id="vacina_retorno_previsto">
                                <div class="form-text text-muted">Data para a próxima aplicação ou validade.</div>
                            </div>
                        </div>
                        
                        <div class="mb-2">
                            <label for="funcionario_id_vacina" class="form-label">Veterinário Aplicador (Opcional)</label>
                            <select class="form-select form-select-sm" id="funcionario_id_vacina" name="funcionario_id_vacina">
                                <option value="">Nenhum Veterinário Atribuído</option>
                                <?php foreach ($veterinarios_select as $vet): ?>
                                    <option value="<?php echo $vet['id']; ?>"><?php echo $vet['nome']; ?></option>
                                <?php endforeach; ?>
                            </select>
                            <div class="form-text text-muted">Apenas para controle interno do aplicador.</div>
                        </div>
                    </div>
                    <div id="campos-consulta" style="display: none;">
                        <div class="mb-2">
                            <label for="consulta_servico_id" class="form-label">Tipo de Consulta *</label>
                            <select class="form-select form-select-sm" id="consulta_servico_id">
                                <option value="">Selecione o Tipo de Consulta</option>
                                <?php foreach ($consultas_select as $consulta): ?>
                                    <option value="<?php echo $consulta['id']; ?>"><?php echo $consulta['nome']; ?></option>
                                <?php endforeach; ?>
                            </select>
                            <div class="form-text text-muted mb-1">Escolha o tipo de atendimento (e.g., Geral, Especialista).</div>
                        </div>

                        <div class="mb-2">
                            <label for="funcionario_id_consulta" class="form-label">Veterinário Responsável *</label>
                            <select class="form-select form-select-sm" id="funcionario_id_consulta">
                                <option value="">Selecione o Veterinário</option>
                                <?php foreach ($veterinarios_select as $vet): ?>
                                    <option value="<?php echo $vet['id']; ?>"><?php echo $vet['nome']; ?></option>
                                <?php endforeach; ?>
                            </select>
                        </div>
                    </div>
                    <div id="campos-comuns-agendamento" style="display: none;">
                        <hr class="mt-3 mb-2">

                        <div class="row">
                            <div class="col-md-6 mb-2">
                                <label for="data_agendamento" class="form-label">Data *</label>
                                <input type="date" class="form-control form-control-sm" id="data_agendamento" name="data_agendamento" required
                                    min="<?php echo date('Y-m-d'); ?>">
                                <div class="form-text text-muted">Data para o serviço.</div>
                            </div>

                            <div class="col-md-6 mb-2">
                                <label for="hora_agendamento" class="form-label">Horário (Opcional)</label>
                                <input type="time" step="900" class="form-control form-control-sm" id="hora_agendamento" name="hora_agendamento">
                                <div class="form-text text-muted">Horário marcado (passo de 15 minutos).</div>
                            </div>
                        </div>
                        
                        <div class="mb-2" id="vacina-retorno-previsto-common" style="display: none;">
                            <label for="vacina_retorno_previsto_hidden" class="form-label">Retorno Previsto (Validade) *</label>
                            <input type="date" class="form-control form-control-sm" id="vacina_retorno_previsto_hidden" name="vacina_retorno_previsto">
                            <div class="form-text text-muted">Data para a próxima aplicação ou validade.</div>
                        </div>

                        <div class="mb-3">
                            <label for="observacoes" class="form-label">Observações</label>
                            <textarea class="form-control form-control-sm" id="observacoes" name="observacoes" rows="2"></textarea>
                        </div>
                    </div>

                </div>
                <div id="total-estimado-area" class="alert alert-info py-1 mb-3" role="alert" style="display: none;">
                    Total Estimado: <strong id="total-estimado">R$ 0,00</strong>
                </div>

                <button type="submit" class="btn btn-success btn-sm mt-2" id="submit-button" disabled><i class="fas fa-check-circle me-2"></i> Confirmar Agendamento</button>
            </div>
        </form>
    </div>
</div>

<script>
$(document).ready(function() {
    // IMPORTANTE: O ID de serviço genérico de vacinação (DEVE existir na tabela `servico`)
    const VACINACAO_ID = <?php echo VACINACAO_ID; ?>; // Agora será 4 (Consulta Veterinária)
    
    // Mapeamento de serviços do PHP para JS - APENAS BANHO/TOSA TÊM PREÇO AQUI
    const SERVICOS_MAP = <?php echo json_encode($servicos_map); ?>;
    
    // Variáveis de estado
    let selectedClienteId = null;
    let selectedPetId = null;
    let selectedPetPorte = 'Pequeno';
    let selectedServiceCategory = ''; // 'banho_tosa', 'vacina', 'consulta'

    // IDs de Serviço (o que será enviado para o backend)
    let servicosAgendados = [];
    
    // --- Funções de Lógica e Cálculo ---

    function getPrecoServico(servicoId) {
        // Apenas serviços Banho/Tosa (IDs 1, 2, 3) têm preço por porte
        if (![1, 2, 3].includes(parseInt(servicoId))) return 0;

        const porte = selectedPetPorte || 'Pequeno';
        
        if (SERVICOS_MAP[servicoId] && SERVICOS_MAP[servicoId][porte]) {
            return parseFloat(SERVICOS_MAP[servicoId][porte]);
        }
        if (SERVICOS_MAP[servicoId] && SERVICOS_MAP[servicoId]['Pequeno']) {
            return parseFloat(SERVICOS_MAP[servicoId]['Pequeno']);
        }
        return 0;
    }

    function calcularTotal() {
        let total = 0;
        servicosAgendados = []; // Reseta a lista de serviços
        $('#vacina_real_id_hidden').val(''); // Reseta o ID real da vacina

        const servicoTipo = $('#servico_tipo_banho_tosa').val();
        const tosaTipo = $('#tosa_tipo').val();

        if (selectedServiceCategory === 'banho_tosa') {
            
            // Lógica de cálculo de preço (Banho/Tosa)
            if (servicoTipo === 'banho') {
                total += getPrecoServico(1);
                servicosAgendados.push(1); // Banho
            } else if (servicoTipo === 'tosa' || servicoTipo === 'banho_e_tosa') {
                let tosaId = null;
                if (tosaTipo === 'higiencia') {
                    tosaId = 2; // Tosa Higiênica
                } else if (tosaTipo === 'completa') {
                    tosaId = 3; // Tosa Completa
                }

                if (tosaId) {
                    total += getPrecoServico(tosaId);

                    if (servicoTipo === 'tosa') {
                        servicosAgendados.push(tosaId);
                    }
                }

                if (servicoTipo === 'banho_e_tosa') {
                    total += getPrecoServico(1);
                    servicosAgendados.push(1); // Banho
                    if (tosaId) {
                         servicosAgendados.push(tosaId);
                    }
                }
            }
            $('#total-estimado-area').show();
            $('#total-estimado').text('R$ ' + total.toFixed(2).replace('.', ','));
            
        } else if (selectedServiceCategory === 'vacina') {
             // Lógica para Vacina:
            const vacinaId = $('#vacina_servico_id').val();
            
            if (vacinaId && !isNaN(parseInt(vacinaId))) {
                 // **CORREÇÃO CRÍTICA APLICADA**: Envia o ID Genérico do Serviço de Vacinação (4) para agendamento.
                servicosAgendados.push(VACINACAO_ID); 
                
                // Armazena o ID da vacina específica para uso do backend em uma tabela auxiliar.
                $('#vacina_real_id_hidden').val(vacinaId); 
            }
            
            $('#total-estimado-area').hide();

        } else if (selectedServiceCategory === 'consulta') {
            // Lógica para Consulta: Apenas preenche o ID do serviço
            const consultaId = $('#consulta_servico_id').val();
            if (consultaId && !isNaN(parseInt(consultaId))) servicosAgendados.push(parseInt(consultaId));
            $('#total-estimado-area').hide();
            
        } else {
            $('#total-estimado-area').hide();
        }

        // Atualiza o JSON (sempre feito)
        $('#servicos_agendados_json').val(JSON.stringify(servicosAgendados));
        
        validateForm();
    }

    // --- Lógica de Validação e Habilitação do Botão (Sem alterações) ---
    function validateForm() {
        let isValid = selectedClienteId && selectedPetId && selectedServiceCategory;
        const submitButton = $('#submit-button');
        const dataAgendamento = $('#data_agendamento').val();
        
        if (!isValid) {
            submitButton.prop('disabled', true);
            return;
        }

        // 1. Validação de Data (Comum e Obrigatória)
        if (!dataAgendamento) {
            isValid = false;
        }

        // 2. Validação por Categoria
        switch (selectedServiceCategory) {
            case 'banho_tosa':
                const tipoBanhoTosa = $('#servico_tipo_banho_tosa').val();
                if (!tipoBanhoTosa) isValid = false;
                if ((tipoBanhoTosa === 'tosa' || tipoBanhoTosa === 'banho_e_tosa') && !$('#tosa_tipo').val()) {
                    isValid = false;
                }
                break;
            case 'vacina':
                // Requer Vacina e Retorno Previsto (checa o campo hidden, que é sincronizado)
                if (!$('#vacina_servico_id').val() || !$('#vacina_retorno_previsto_hidden').val()) {
                    isValid = false;
                }
                break;
            case 'consulta':
                // Requer Tipo de Consulta e Veterinário
                if (!$('#consulta_servico_id').val() || !$('#funcionario_id_consulta').val()) {
                    isValid = false;
                }
                break;
            default:
                isValid = false;
        }

        submitButton.prop('disabled', !isValid);
    }
    
    // --- Lógica de Exibição Progressiva e Eventos (Sem alterações relevantes) ---

    // 1. Busca de Cliente
    $('#search_cliente').on('input', function() {
        const termo = $(this).val().trim();
        const resultsDiv = $('#search-cliente-results');
        resultsDiv.show();
        resultsDiv.empty();
        
        // Reseta etapas seguintes ao iniciar nova busca
        selectedClienteId = null;
        selectedPetId = null;
        selectedPetPorte = 'Pequeno';
        selectedServiceCategory = '';
        $('#cliente_id_hidden').val('');
        $('#pet_id_hidden').val('');
        $('#pet_porte_hidden').val('');
        $('#tipo_servico_principal').val('');
        $('#servico_categoria').val('');
        $('#vacina_real_id_hidden').val(''); // Reseta

        
        $('#step-pet').hide();
        $('#step-agendamento').hide();
        $('#selected-cliente-info').hide();
        $('#pet_porte_info').hide();
        $('#servico-campos-especificos').children().hide();
        $('#campos-comuns-agendamento').hide();
        $('#vacina-retorno-previsto-common').hide();

        if (termo.length < 3) {
            validateForm();
            return;
        }
        
        // Lógica AJAX para buscar clientes
        $.ajax({
            url: 'search_data.php?type=client&term=' + encodeURIComponent(termo),
            method: 'GET',
            dataType: 'json',
            success: function(data) {
                if (data.length > 0) {
                    data.forEach(function(cliente) {
                        const item = `<a href="#" class="list-group-item list-group-item-action list-group-item-sm" 
                                            data-id="${cliente.id}" data-nome="${cliente.nome}">
                                            ${cliente.nome} (${cliente.cpf || 'Não Informado'})
                                        </a>`;
                        resultsDiv.append(item);
                    });
                } else {
                    resultsDiv.append('<div class="list-group-item">Nenhum cliente encontrado.</div>');
                }
            },
            error: function() {
                resultsDiv.append('<div class="list-group-item list-group-item-danger">Erro ao buscar clientes.</div>');
            }
        });
        validateForm();
    });

    $('#search-cliente-results').on('click', 'a', function(e) {
        e.preventDefault();
        selectedClienteId = $(this).data('id');
        const clienteNome = $(this).data('nome');

        $('#cliente_id_hidden').val(selectedClienteId);
        $('#selected-cliente-info').show().find('strong').text(clienteNome);
        $('#search_cliente').prop('disabled', true); 
        $('#search-cliente-results').empty().hide();

        loadPets(selectedClienteId);
        validateForm();
    });

    // 2. Busca de Pet 
    function loadPets(clienteId) {
        $('#step-pet').show();
        const petSelect = $('#pet_id_select');
        petSelect.empty().append('<option value="">Carregando Pets...</option>').prop('disabled', true);
        
         $.ajax({
            url: 'search_data.php?type=pet&client_id=' + clienteId,
            method: 'GET',
            dataType: 'json',
            success: function(data) {
                petSelect.empty().append('<option value="" disabled selected>Selecione o Pet</option>');
                if (data.length > 0) {
                    data.forEach(function(pet) {
                        const porteDisplay = pet.porte || 'Pequeno';
                        petSelect.append(`<option 
                            value="${pet.id}" 
                            data-porte="${porteDisplay}">
                            ${pet.nome} (${pet.raca_nome || 'Raça Não Informada'}) - Porte: ${porteDisplay}
                        </option>`);
                    });
                    petSelect.prop('disabled', false);
                } else {
                    petSelect.append('<option value="" disabled>Nenhum pet encontrado para este cliente.</option>');
                }
                validateForm();
            },
            error: function() {
                petSelect.empty().append('<option value="" disabled>Erro ao carregar pets.</option>');
                validateForm();
            }
        });
    }

    // Seleção de Pet
    $('#pet_id_select').on('change', function() {
        const selectedOption = $(this).find('option:selected');
        selectedPetId = $(this).val();
        
        if (selectedPetId) {
            selectedPetPorte = selectedOption.data('porte') || 'Pequeno'; 
            const petNomeCompleto = selectedOption.text();

            $('#pet_id_hidden').val(selectedPetId);
            $('#selected-pet-info').show().find('strong').text(petNomeCompleto); 
            
            $('#pet_porte_display').text(selectedPetPorte);
            $('#pet_porte_hidden').val(selectedPetPorte);
            $('#pet_porte_info').show();

            $('#step-agendamento').show();
            calcularTotal();
        } else {
            $('#step-agendamento').hide();
            $('#selected-pet-info').hide();
            $('#pet_porte_info').hide();
            selectedPetPorte = 'Pequeno'; 
            selectedPetId = null;
        }
        validateForm();
    });

    // 3. Lógica de Serviços (Principal: Categoria)
    $('#servico_categoria').on('change', function() {
        selectedServiceCategory = $(this).val();
        $('#tipo_servico_principal').val(selectedServiceCategory);
        
        // Reseta campos específicos e comuns
        $('#servico-campos-especificos').children().hide();
        $('#campos-comuns-agendamento').hide();
        $('#tosa-options').hide();
        $('#vacina-retorno-previsto-common').hide();

        // Limpa campos de seleção para forçar nova seleção/validação
        $('#servico_tipo_banho_tosa').val('');
        $('#tosa_tipo').val('');
        $('#vacina_servico_id').val('');
        $('#consulta_servico_id').val('');
        $('#funcionario_id_consulta').val('');
        // Importante: limpa os dois campos de data da vacina
        $('#vacina_retorno_previsto').val('');
        $('#vacina_retorno_previsto_hidden').val(''); 
        $('#vacina_real_id_hidden').val(''); // Reseta ID real da vacina

        servicosAgendados = [];

        if (selectedServiceCategory) {
            $('#campos-comuns-agendamento').show();

            // Mostra campos específicos baseados na categoria
            switch (selectedServiceCategory) {
                case 'banho_tosa':
                    $('#campos-banho-tosa').show();
                    break;
                case 'vacina':
                    $('#campos-vacina').show();
                    $('#vacina-retorno-previsto-common').show();
                    break;
                case 'consulta':
                    $('#campos-consulta').show();
                    break;
            }
        }
        
        calcularTotal();
        validateForm();
    });

    // 4. Lógica de Serviços (Sub-seleção Banho/Tosa)
    
    // Controle da visibilidade das opções de Tosa
    $('#servico_tipo_banho_tosa').on('change', function() {
        const tipo = $(this).val();
        const tosaOptions = $('#tosa-options');
        const tosaSelect = $('#tosa_tipo');
        
        if (tipo === 'tosa' || tipo === 'banho_e_tosa') {
            tosaOptions.show();
        } else {
            tosaOptions.hide();
            tosaSelect.val('');
        }
        
        calcularTotal();
    });

    // Recalcula o total ao selecionar o tipo de tosa
    $('#tosa_tipo').on('change', calcularTotal);
    
    // SINCRONIZAÇÃO CRÍTICA
    // Copia o valor do campo de data visível (sem required) para o campo hidden (com name)
    $('#vacina_retorno_previsto').on('change', function() {
        const dataRetorno = $(this).val();
        $('#vacina_retorno_previsto_hidden').val(dataRetorno);
        calcularTotal(); // Chama calcularTotal para atualizar JSON e validar
    });

    // Recalcula/valida ao selecionar a vacina/consulta ou outros campos comuns
    $('#vacina_servico_id, #consulta_servico_id, #funcionario_id_consulta, #data_agendamento, #hora_agendamento').on('change', function() {
        calcularTotal();
    });

    // --- Lógica de Envio do Formulário (AJAX) ---
    $('#form-agendar-servico').on('submit', function(e) {
        e.preventDefault(); 

        // Se o botão estiver desabilitado (validação JS falhou), não envia
        if ($('#submit-button').prop('disabled')) return;

        const form = $(this);
        const btnSubmit = $('#submit-button');
        const statusArea = $('#status-message-area');

        btnSubmit.prop('disabled', true).html('<i class="fas fa-spinner fa-spin me-2"></i> Processando...');
        statusArea.empty(); 

        $.ajax({
            url: form.attr('action'),
            type: 'POST',
            data: form.serialize(),
            dataType: 'json',
            success: function(response) {
                
                if (response.success) {
                    statusArea.html('<div class="alert alert-success mt-3">' + response.message + '</div>');
                    
                    // Reseta o formulário e o estado
                    form[0].reset(); 
                    selectedClienteId = null;
                    selectedPetId = null;
                    selectedPetPorte = 'Pequeno';
                    selectedServiceCategory = '';

                    // Esconde as etapas seguintes e reabilita a busca
                    $('#selected-cliente-info, #selected-pet-info, #pet_porte_info').hide();
                    $('#pet_id_select').empty().prop('disabled', true);
                    $('#step-pet').hide();
                    $('#step-agendamento').hide();
                    $('#search_cliente').prop('disabled', false).val(''); 
                    $('#total-estimado-area').hide();

                } else {
                    statusArea.html('<div class="alert alert-danger mt-3">' + response.message + '</div>');
                }
                
                btnSubmit.html('<i class="fas fa-check-circle me-2"></i> Confirmar Agendamento');
                validateForm(); 
            },
            error: function(xhr, status, error) {
                let errorMessage = "Erro de comunicação com o servidor ao processar o agendamento.";
                if (xhr.status === 500) {
                     errorMessage += " (Erro Interno do Servidor)";
                }
                
                statusArea.html('<div class="alert alert-danger mt-3">' + errorMessage + '</div>');
                
                btnSubmit.html('<i class="fas fa-check-circle me-2"></i> Confirmar Agendamento');
                validateForm(); 
            }
        });
    });

    // Inicialização
    validateForm(); 
});
</script>