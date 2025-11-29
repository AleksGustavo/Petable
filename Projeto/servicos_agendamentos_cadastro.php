<?php

// Inclui o arquivo de conexão e verifica a sessão, se necessário (boa prática)
require_once 'conexao.php';

// Verifica a conexão mysqli (necessário antes de qualquer query)
if (empty($conexao)) {
    // Se a conexão falhar, apenas exibe um erro e sai, evitando problemas de header.
    echo '<div class="alert alert-danger">Erro crítico: Conexão mysqli indisponível.</div>';
    exit(); // CORREÇÃO: Faltava o parêntese de fechamento do exit()
}

// --- 1. Mapeamento de IDs e Busca de Dados Necessários ---

const ID_SERVICO_BANHO_TOSA = [1, 2, 3]; // Banho, Tosa Higiênica, Tosa Completa
const ID_SERVICO_VACINACAO = 10; // ID do serviço GERAL 'Vacinação' na tabela 'servico'
const ID_SERVICO_CONSULTA = [20, 21]; // Ex: Consulta Geral, Retorno

$all_servico_ids = array_merge(ID_SERVICO_BANHO_TOSA, ID_SERVICO_CONSULTA, [ID_SERVICO_VACINACAO]);
$servicos_map = []; // Contém todos os serviços (nomes e preços por porte)

// A. Buscar TODOS os Serviços Ativos (Nomes e IDs)
// Usa prepared statement para IDs
$in_placeholders = implode(',', array_fill(0, count($all_servico_ids), '?'));
$sql_servicos = "SELECT id, nome, ativo FROM servico WHERE id IN ({$in_placeholders}) AND ativo = 1";

$stmt_servicos = mysqli_prepare($conexao, $sql_servicos);
$types_servicos = str_repeat('i', count($all_servico_ids));

if (!mysqli_stmt_bind_param($stmt_servicos, $types_servicos, ...$all_servico_ids)) {
    die("Erro ao vincular parâmetros para a busca de serviços: " . mysqli_stmt_error($stmt_servicos));
}
mysqli_stmt_execute($stmt_servicos); // CORREÇÃO: Execução garantida.
$result_servicos = mysqli_stmt_get_result($stmt_servicos);
$servicos_lista = mysqli_fetch_all($result_servicos, MYSQLI_ASSOC);
mysqli_stmt_close($stmt_servicos);


// Mapeia serviços para uso no JS
foreach ($servicos_lista as $s) {
    $servicos_map[$s['id']] = ['nome' => $s['nome'], 'id' => $s['id']];
}

// B. Buscar Preços por Porte (Apenas para Banho e Tosa)
$banho_tosa_ids = ID_SERVICO_BANHO_TOSA;
$in_placeholders_b_t = implode(',', array_fill(0, count($banho_tosa_ids), '?'));
$sql_precos_porte = "SELECT servico_id, porte, preco 
                     FROM preco_servico_porte 
                     WHERE servico_id IN ({$in_placeholders_b_t})";

$stmt_precos = mysqli_prepare($conexao, $sql_precos_porte);
$types_precos = str_repeat('i', count($banho_tosa_ids));

if (!mysqli_stmt_bind_param($stmt_precos, $types_precos, ...$banho_tosa_ids)) {
    die("Erro ao vincular parâmetros para a busca de preços: " . mysqli_stmt_error($stmt_precos));
}
mysqli_stmt_execute($stmt_precos); 
$result_precos_porte = mysqli_stmt_get_result($stmt_precos);
// O loop while segue, processando os resultados
while ($row = mysqli_fetch_assoc($result_precos_porte)) {
    if (isset($servicos_map[$row['servico_id']])) {
        $servicos_map[$row['servico_id']][$row['porte']] = (float)$row['preco'];
    }
}
mysqli_stmt_close($stmt_precos);


// C. Buscar Funcionários (Groomers e Veterinários)
$sql_funcionarios = "SELECT f.id, f.nome
                     FROM funcionario f
                     JOIN usuario u ON f.usuario_id = u.id
                     WHERE u.ativo = 1 
                     ORDER BY f.nome ASC";
$result_funcionarios = mysqli_query($conexao, $sql_funcionarios);
$funcionarios = mysqli_fetch_all($result_funcionarios, MYSQLI_ASSOC);


// D. Buscar Catálogo de Vacinas (da tabela 'vacina')
$sql_catalogo_vacinas = "SELECT id, nome, doenca_protecao, validade_padrao_meses 
                         FROM vacina 
                         WHERE ativo = 1 
                         ORDER BY nome ASC";
$result_catalogo_vacinas = mysqli_query($conexao, $sql_catalogo_vacinas);
$catalogo_vacinas = mysqli_fetch_all($result_catalogo_vacinas, MYSQLI_ASSOC);

// Mapear o catálogo para uso em JavaScript
$catalogo_vacinas_map = [];
foreach ($catalogo_vacinas as $vacina) {
    $catalogo_vacinas_map[$vacina['id']] = [
        'nome' => $vacina['nome'],
        'validade_meses' => (int)$vacina['validade_padrao_meses']
    ];
}

// E. NOVA QUERY: Buscar Clientes (ALTERAÇÃO: removido CPF)
$sql_clientes = "SELECT id, nome FROM cliente WHERE ativo = 1 ORDER BY nome ASC";
$result_clientes = mysqli_query($conexao, $sql_clientes);
$clientes_select = mysqli_fetch_all($result_clientes, MYSQLI_ASSOC);

// --- Separação da Lista de Serviços para os Selects (HTML) ---

$vacinas_select = $catalogo_vacinas;
$servico_vacinacao_id = ID_SERVICO_VACINACAO;

$consultas_select = array_filter($servicos_lista, function ($s) {
    // Acessando a constante ID_SERVICO_CONSULTA corretamente.
    return in_array($s['id'], ID_SERVICO_CONSULTA);
});

$veterinarios_select = $funcionarios;
$groomers_select = $funcionarios;

// Fechamento da conexão no final do script
mysqli_close($conexao);
?>

<div class="container-fluid">
    <h3 class="mb-4">Novo Agendamento de Serviço</h3>

    <form id="agendamento-form" method="POST" action="servicos_processar_agendamento.php">

        <input type="hidden" name="servicos_agendados_json" id="servicos_agendados_json">

        <input type="hidden" name="total_estimado" id="total_estimado_input" value="0.00">
        <div id="step-servico" class="card mb-3">
            <div class="card-header">
                <h5 class="mb-0">Selecione a Categoria do Serviço</h5>
            </div>
            <div class="card-body">
                <div class="row">
                    <div class="col-md-4">
                        <div class="form-check form-check-inline">
                            <input class="form-check-input servico-category" type="radio" name="tipo_servico_principal" id="tipo_servico_banho_tosa" value="banho_tosa">
                            <label class="form-check-label" for="tipo_servico_banho_tosa">Banho e Tosa</label>
                        </div>
                    </div>
                    <div class="col-md-4">
                        <div class="form-check form-check-inline">
                            <input class="form-check-input servico-category" type="radio" name="tipo_servico_principal" id="tipo_servico_vacina" value="vacina">
                            <label class="form-check-label" for="tipo_servico_vacina">Vacinação</label>
                        </div>
                    </div>
                    <div class="col-md-4">
                        <div class="form-check form-check-inline">
                            <input class="form-check-input servico-category" type="radio" name="tipo_servico_principal" id="tipo_servico_consulta" value="consulta">
                            <label class="form-check-label" for="tipo_servico_consulta">Consulta/Atendimento</label>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div id="servico-campos-especificos" class="card mb-3" style="display: none;">
            <div class="card-header">
                <h5 class="mb-0">Detalhes do Serviço</h5>
            </div>
            <div class="card-body">

                <div id="campos-banho-tosa" style="display: none;">
                    <div class="row">
                        <div class="col-md-6 mb-2">
                            <label for="servico_tipo_banho_tosa" class="form-label">Tipo Principal</label>
                            <select class="form-select form-select-sm servico-input" id="servico_tipo_banho_tosa">
                                <option value="">Selecione...</option>
                                <option value="banho">Apenas Banho</option>
                                <option value="tosa">Apenas Tosa</option>
                                <option value="banho_e_tosa">Banho e Tosa</option>
                            </select>
                        </div>
                        <div class="col-md-6 mb-2" id="tosa-tipo-area" style="display: none;">
                            <label for="tosa_tipo" class="form-label">Tipo de Tosa</label>
                            <select class="form-select form-select-sm servico-input" id="tosa_tipo">
                                <option value="">Selecione...</option>
                                <option value="higiencia">Higiênica (ID 2)</option>
                                <option value="completa">Completa (ID 3)</option>
                            </select>
                        </div>
                    </div>
                    <div id="total-estimado-area" class="alert alert-info mt-3" style="display: none;">
                        Total Estimado (Baseado no Porte do Pet): <strong id="total-estimado">R$ 0,00</strong>
                    </div>
                </div>

                <div id="campos-vacina" style="display: none;">
                    <input type="hidden" id="servico_principal_vacina_id" value="<?php echo $servico_vacinacao_id; ?>" name="servico_id_vacina_principal">

                    <div class="mb-2">
                        <label for="vacina_catalogo_nome" class="form-label">Vacina a ser Aplicada (Catálogo ou Nova)</label>
                        <input type="text"
                            class="form-control form-control-sm servico-input"
                            id="vacina_catalogo_nome"
                            name="vacina_catalogo_nome"
                            list="lista_vacinas"
                            placeholder="Digite ou selecione uma vacina...">
                        <datalist id="lista_vacinas"></datalist>
                        <div class="form-text text-muted mb-1">Você pode digitar o nome de uma nova vacina caso não esteja na lista.</div>
                    </div>

                    <input type="hidden" id="vacina_catalogo_id" name="vacina_catalogo_id" value="">

                    <div class="row">
                        <div class="col-md-6 mb-2">
                            <label for="vacina_retorno_previsto" class="form-label">Retorno Previsto (Validade) *</label>
                            <input type="date" class="form-control form-control-sm servico-input" id="vacina_retorno_previsto" name="vacina_retorno_previsto">
                            <div class="form-text text-muted">Data para a próxima aplicação ou validade. (Calculado automaticamente)</div>
                        </div>
                    </div>
                </div>

                <div id="campos-consulta" style="display: none;">
                    <div class="mb-2">
                        <label for="consulta_servico_id" class="form-label">Tipo de Consulta</label>
                        <select class="form-select form-select-sm servico-input" id="consulta_servico_id">
                            <option value="">Selecione a Consulta</option>
                            <?php foreach ($consultas_select as $consulta): ?>
                                <option value="<?php echo $consulta['id']; ?>">
                                    <?php echo $consulta['nome']; ?> (ID <?php echo $consulta['id']; ?>)
                                </option>
                            <?php endforeach; ?>
                        </select>
                    </div>
                </div>

            </div>
        </div>

        <div id="step-cliente" class="card mb-3" style="display: none;">
            <div class="card-header">
                <h5 class="mb-0">Selecione o Cliente</h5>
            </div>
            <div class="card-body">
                <div class="mb-3">
                    <label for="cliente_id" class="form-label">Cliente *</label>
                    <select class="form-select form-select-sm" id="cliente_id" name="cliente_id" required>
                        <option value="">Selecione um Cliente</option>
                        <?php foreach ($clientes_select as $cliente): ?>
                            <option value="<?php echo $cliente['id']; ?>">
                                <?php echo htmlspecialchars($cliente['nome']); ?> (ID: <?php echo htmlspecialchars($cliente['id']); ?>)
                            </option>
                        <?php endforeach; ?>
                    </select>
                </div>
            </div>
        </div>

        <div id="step-pet" class="card mb-3" style="display: none;">
            <div class="card-header">
                <h5 class="mb-0">Selecione o Pet</h5>
            </div>
            <div class="card-body">
                <div class="mb-3">
                    <label for="pet_id" class="form-label">Pet (Porte: <span id="pet-porte-display">Pequeno</span>) *</label>
                    <select class="form-select form-select-sm" id="pet_id" name="pet_id" required disabled>
                        <option value="">Selecione um Cliente primeiro...</option>
                    </select>
                    <div class="form-text text-muted">A seleção do pet é essencial para calcular o preço (banho/tosa) e garantir o histórico (vacina/consulta).</div>
                </div>
            </div>
        </div>

        <div id="step-agendamento-final" class="card mb-3" style="display: none;">
            <div class="card-header">
                <h5 class="mb-0">Data e Responsável</h5>
            </div>
            <div class="card-body">

                <div class="row mb-3">
                    <div class="col-md-6">
                        <label for="data_agendamento" class="form-label">Data do Agendamento *</label>
                        <input type="date" class="form-control form-control-sm" id="data_agendamento" name="data_agendamento" required>
                    </div>
                    <div class="col-md-6">
                        <label for="hora_agendamento" class="form-label">Hora do Agendamento *</label>
                        <input type="time" class="form-control form-control-sm" id="hora_agendamento" name="hora_agendamento" required>
                    </div>
                </div>

                <div class="funcionario-campo" id="funcionario_campo_banhotosa" style="display: none;">
                    <label for="funcionario_id_banhotosa" class="form-label">Groomer / Banhista Responsável *</label>
                    <select class="form-select form-select-sm" id="funcionario_id_banhotosa" name="funcionario_id_banhotosa">
                        <option value="">Selecione o Groomer/Banhista</option>
                        <?php foreach ($groomers_select as $f): ?>
                            <option value="<?php echo $f['id']; ?>"><?php echo $f['nome']; ?></option>
                        <?php endforeach; ?>
                    </select>
                </div>

                <div class="funcionario-campo" id="funcionario_campo_vacina" style="display: none;">
                    <label for="funcionario_id_vacina" class="form-label">Veterinário Aplicador (Vacina) *</label>
                    <select class="form-select form-select-sm" id="funcionario_id_vacina" name="funcionario_id_vacina">
                        <option value="">Selecione o Veterinário</option>
                        <?php foreach ($veterinarios_select as $f): ?>
                            <option value="<?php echo $f['id']; ?>"><?php echo $f['nome']; ?></option>
                        <?php endforeach; ?>
                    </select>
                </div>

                <div class="funcionario-campo" id="funcionario_campo_consulta" style="display: none;">
                    <label for="funcionario_id_consulta" class="form-label">Veterinário Responsável (Consulta) *</label>
                    <select class="form-select form-select-sm" id="funcionario_id_consulta" name="funcionario_id_consulta">
                        <option value="">Selecione o Veterinário</option>
                        <?php foreach ($veterinarios_select as $f): ?>
                            <option value="<?php echo $f['id']; ?>"><?php echo $f['nome']; ?></option>
                        <?php endforeach; ?>
                    </select>
                </div>

                <div class="mb-3 mt-3">
                    <label for="observacoes" class="form-label">Observações</label>
                    <textarea class="form-control form-control-sm" id="observacoes" name="observacoes" rows="2"></textarea>
                </div>

            </div>
        </div>

        <button type="submit" id="submit-button" class="btn btn-primary" disabled>Agendar Serviço</button>
        <button type="button" id="reset-button" class="btn btn-secondary">Limpar / Novo Agendamento</button>

    </form>
</div>

<script>
    $(document).ready(function() {
        // Mapeamento de serviços do PHP para JS 
        const SERVICOS_MAP = <?php echo json_encode($servicos_map); ?>;

        // Variáveis de estado
        let selectedClienteId = null;
        let selectedPetId = null;
        let selectedPetPorte = 'Pequeno';
        let selectedPetEspecie = ''; // Armazena a espécie do Pet
        let selectedServiceCategory = '';

        let servicosAgendados = [];

        // --- Funções de Lógica e Cálculo ---

        function calcularDataRetorno(meses) {
            if (meses <= 0) return '';

            const hoje = new Date();
            // CORREÇÃO: Usa 'hoje.getTime()' para preservar a data original antes de somar meses
            const dataRetorno = new Date(hoje.getTime()); 
            dataRetorno.setMonth(dataRetorno.getMonth() + meses);

            const ano = dataRetorno.getFullYear();
            const mes = String(dataRetorno.getMonth() + 1).padStart(2, '0');
            const dia = String(dataRetorno.getDate()).padStart(2, '0');

            return `${ano}-${mes}-${dia}`;
        }

        function getPrecoServico(servicoId) {
            // IDs de Banho/Tosa são 1, 2, 3
            if (![1, 2, 3].includes(parseInt(servicoId))) return 0;

            const porte = selectedPetPorte || 'Pequeno';

            if (SERVICOS_MAP[servicoId] && SERVICOS_MAP[servicoId][porte]) {
                // O PHP enviou o preço como float.
                return parseFloat(SERVICOS_MAP[servicoId][porte]);
            }
            // Se não houver preço para o porte específico, tenta o Pequeno como fallback (opcional)
            if (SERVICOS_MAP[servicoId] && SERVICOS_MAP[servicoId]['Pequeno']) {
                return parseFloat(SERVICOS_MAP[servicoId]['Pequeno']);
            }
            return 0;
        }

        function calcularTotal() {
            let total = 0.00; // Usar float para cálculo
            servicosAgendados = [];
            let requiredFieldsComplete = false;

            $('#total-estimado-area').hide();
            $('#servicos_agendados_json').val('');
            $('#total_estimado_input').val('0.00'); // Resetar o campo hidden

            if (selectedServiceCategory === 'banho_tosa') {
                const servicoTipo = $('#servico_tipo_banho_tosa').val();
                const tosaTipo = $('#tosa_tipo').val();

                if (servicoTipo) {
                    requiredFieldsComplete = true;

                    if (servicoTipo === 'banho') {
                        total += getPrecoServico(1);
                        servicosAgendados.push(1);
                    } else if (servicoTipo === 'tosa' || servicoTipo === 'banho_e_tosa') {
                        let tosaId = null;
                        if (tosaTipo === 'higiencia') {
                            tosaId = 2;
                        } else if (tosaTipo === 'completa') {
                            tosaId = 3;
                        }

                        if (!tosaId) requiredFieldsComplete = false;

                        if (tosaId) {
                            total += getPrecoServico(tosaId);

                            if (servicoTipo === 'tosa') {
                                servicosAgendados.push(tosaId);
                            }
                        }

                        if (servicoTipo === 'banho_e_tosa') {
                            total += getPrecoServico(1);
                            servicosAgendados.push(1);
                            if (tosaId) {
                                servicosAgendados.push(tosaId);
                            }
                        }
                    }
                } else {
                    requiredFieldsComplete = false;
                }

                if (total > 0) {
                    $('#total-estimado-area').show();
                    $('#total-estimado').text('R$ ' + total.toFixed(2).replace('.', ','));
                }

            } else if (selectedServiceCategory === 'vacina') {
                const vacinaCatalogoId = $('#vacina_catalogo_id').val();
                const servicoPrincipalId = $('#servico_principal_vacina_id').val();

                // Requer que a vacina e o id principal do serviço (10) estejam preenchidos
                requiredFieldsComplete = vacinaCatalogoId !== '' && servicoPrincipalId !== '';

                if (requiredFieldsComplete) {
                    servicosAgendados.push(parseInt(servicoPrincipalId));

                    // O detalhe da vacina precisa ir como um objeto no array para ser parseado pelo PHP
                    servicosAgendados.push({
                        vacina_catalogo_id: parseInt(vacinaCatalogoId)
                    });
                }

            } else if (selectedServiceCategory === 'consulta') {
                const consultaId = $('#consulta_servico_id').val();
                requiredFieldsComplete = consultaId !== '';
                if (consultaId) servicosAgendados.push(parseInt(consultaId));

            } else {
                requiredFieldsComplete = false;
            }

            if (servicosAgendados.length > 0) {
                $('#servicos_agendados_json').val(JSON.stringify(servicosAgendados));
            }

            // ATUALIZAÇÃO CRÍTICA: Enviar o total para o PHP (para a coluna total_estimado)
            $('#total_estimado_input').val(total.toFixed(2));


            return requiredFieldsComplete;
        }

        function updateFinalStepVisibility() {
            $('.funcionario-campo').hide();
            // Remove o atributo 'required' de todos os campos de funcionário para não interferir na validação
            $('.funcionario-campo select').prop('required', false);

            const vacinaRetornoCampo = $('#vacina_retorno_previsto');
            vacinaRetornoCampo.prop('required', false);

            let isFinalStepValid = true;
            let funcionarioFieldRequired = null; // Para verificar o campo específico

            switch (selectedServiceCategory) {
                case 'banho_tosa':
                    $('#funcionario_campo_banhotosa').show();
                    funcionarioFieldRequired = $('#funcionario_id_banhotosa');
                    break;
                case 'vacina':
                    $('#funcionario_campo_vacina').show();
                    funcionarioFieldRequired = $('#funcionario_id_vacina');

                    if ($('#vacina_catalogo_id').val()) {
                        vacinaRetornoCampo.prop('required', true);
                        if (!vacinaRetornoCampo.val()) isFinalStepValid = false;
                    }
                    break;
                case 'consulta':
                    $('#funcionario_campo_consulta').show();
                    funcionarioFieldRequired = $('#funcionario_id_consulta');
                    break;
            }

            // Valida o campo de funcionário/veterinário específico
            if (funcionarioFieldRequired) {
                funcionarioFieldRequired.prop('required', true); // Define como required apenas o campo visível
                if (!funcionarioFieldRequired.val()) isFinalStepValid = false;
            }

            // Validação de Data/Hora
            if (!$('#data_agendamento').val() || !$('#hora_agendamento').val()) isFinalStepValid = false;

            // Só mostra o Step 4 se Categoria, Cliente, Pet e Sub-serviços estiverem OK
            if (selectedServiceCategory && selectedClienteId && selectedPetId && calcularTotal()) {
                $('#step-agendamento-final').show();
                return isFinalStepValid;
            } else {
                $('#step-agendamento-final').hide();
                return false;
            }
        }

        function validateForm() {
            const isServiceSelected = calcularTotal();
            const isClientSelected = selectedClienteId !== null;
            const isPetSelected = selectedPetId !== null;

            const isFinalStepValid = updateFinalStepVisibility();

            const isValid = isServiceSelected && isClientSelected && isPetSelected && isFinalStepValid;

            $('#submit-button').prop('disabled', !isValid);

            // Revalida a visibilidade dos passos intermediários
            if (selectedServiceCategory) {
                $('#servico-campos-especificos').show();
                if (isServiceSelected) {
                    $('#step-cliente').show();
                    if (isClientSelected) {
                        $('#step-pet').show();
                    } else {
                        $('#step-pet').hide(); // Esconde Pet se Cliente não estiver selecionado
                    }
                } else {
                    $('#step-cliente').hide(); // Esconde Cliente e Pet se o Serviço não estiver válido
                    $('#step-pet').hide();
                }
            } else {
                $('#servico-campos-especificos').hide();
                $('#step-cliente').hide();
                $('#step-pet').hide();
            }
        }

        // --- Funções de Reset e Eventos ---

        function resetSteps(startStep = 1) {
            selectedClienteId = null;
            selectedPetId = null;
            selectedPetPorte = 'Pequeno';
            selectedPetEspecie = ''; // Reset do campo de espécie
            selectedServiceCategory = '';
            servicosAgendados = [];

            $('.servico-category').prop('checked', false);
            $('.servico-input').val('');
            $('#total-estimado-area').hide();
            $('#servicos_agendados_json').val('');
            $('#total_estimado_input').val('0.00'); // Resetar
            $('#tosa-tipo-area').hide();
            $('#pet-porte-display').text('Pequeno');

            $('#data_agendamento').val('');
            $('#hora_agendamento').val('');
            $('#observacoes').val('');
            $('.funcionario-campo select').val('').prop('required', false); // Limpa e remove required

            // LIMPA CLIENTE E PET
            $('#cliente_id').val('');
            $('#pet_id').html('<option value="">Selecione um Cliente primeiro...</option>').prop('disabled', true);

            $('#campos-banho-tosa, #campos-vacina, #campos-consulta').hide();

            if (startStep <= 4) {
                $('#step-agendamento-final').hide();
            }

            validateForm();
        }

        /**
         * FUNÇÃO AJAX PARA BUSCAR PETS
         */
        function loadPets(clienteId) {
            const $petSelect = $('#pet_id');
            $petSelect.prop('disabled', true).html('<option value="">Carregando Pets...</option>');
            selectedPetId = null;
            selectedPetPorte = 'Pequeno';
            selectedPetEspecie = ''; // Reset da espécie
            $('#pet-porte-display').text('Pequeno');

            if (!clienteId) {
                $petSelect.html('<option value="">Selecione um Cliente primeiro...</option>');
                validateForm();
                return;
            }

            // Chamada AJAX para o novo arquivo (a ser criado)
            $.ajax({
                url: 'servicos_buscar_pets.php',
                method: 'GET',
                data: {
                    cliente_id: clienteId
                },
                dataType: 'json',
                success: function(pets) {
                    let options = '<option value="">Selecione o Pet</option>';
                    if (pets.length > 0) {
                        pets.forEach(function(pet) {
                            // ALTERAÇÃO: Adicionando o atributo data-especie
                            options += `<option value="${pet.id}" data-porte="${pet.porte}" data-especie="${pet.especie || ''}">${pet.nome} (${pet.porte} - ${pet.especie || 'N/A'})</option>`;
                        });
                        $petSelect.prop('disabled', false).html(options);
                    } else {
                        $petSelect.html('<option value="">Nenhum Pet encontrado para este Cliente</option>');
                    }
                    validateForm();
                },
                error: function(xhr, status, error) {
                    console.error("Erro ao carregar pets:", error, xhr.responseText);
                    $petSelect.html('<option value="">Erro ao carregar pets</option>');
                    alert("Erro ao carregar pets do cliente. Verifique o console ou a resposta do servidor.");
                    validateForm();
                }
            });
        }

        // ----------------------------------------------------------------------
        // --- Lógica de Eventos ---
        // ----------------------------------------------------------------------

        // 1. Mudança de Categoria Principal
        $('.servico-category').on('change', function() {
            selectedServiceCategory = $(this).val();
            $('#campos-banho-tosa, #campos-vacina, #campos-consulta').hide();

            if (selectedServiceCategory === 'banho_tosa') {
                $('#campos-banho-tosa').show();
            } else if (selectedServiceCategory === 'vacina') {
                $('#campos-vacina').show();
            } else if (selectedServiceCategory === 'consulta') {
                $('#campos-consulta').show();
            }

            // Reset dos campos de sub-serviços para garantir a validação
            $('.servico-input').val('');

            validateForm();
        });

        // 1.1 Lógica Tosa 
        $('#servico_tipo_banho_tosa, #tosa_tipo').on('change', function() {
            const tipo = $('#servico_tipo_banho_tosa').val();
            if (tipo === 'tosa' || tipo === 'banho_e_tosa') {
                $('#tosa-tipo-area').show();
            } else {
                $('#tosa-tipo-area').hide();
                $('#tosa_tipo').val('');
            }
            calcularTotal();
            validateForm();
        });

        // 1.2 Lógica Vacina (UNIFICADA: Busca e Seleção)
        
        /**
         * Busca dinâmica de vacinas - Disparada ao digitar no campo.
         */
        $('#vacina_catalogo_nome').on('input', function() {
            const termo = $(this).val().trim();
            const $datalist = $('#lista_vacinas');
            $datalist.empty(); 
            
            // Requisito: Só busca se tiver pelo menos 2 caracteres
            if (termo.length < 2) return; 

            $.ajax({
                url: 'servicos_buscar_vacinas.php',
                method: 'GET',
                data: {
                    q: termo
                },
                dataType: 'json',
                success: function(vacinas) {
                    if (vacinas.error) {
                        console.error("Erro do servidor (PHP):", vacinas.error);
                        return;
                    }

                    let options = '';
                    vacinas.forEach(function(v) {
                        // v.validade é o alias retornado pelo PHP
                        const validadeMeses = parseInt(v.validade) || 0;
                        
                        options += `<option value="${v.nome}" 
                                           data-id="${v.id}" 
                                           data-validade="${validadeMeses}" 
                                           title="Proteção: ${v.doenca_protecao} | Validade: ${validadeMeses} meses">
                                    </option>`;
                    });
                    $('#lista_vacinas').html(options);
                },
                error: function(xhr, status, error) {
                    console.error("Falha na chamada AJAX para buscar vacinas. Verifique o Network (F12).", error);
                }
            });
        });

        /**
         * Atualiza hidden ID e calcula data de retorno ao selecionar/mudar o campo (Evento 'change').
         */
        $('#vacina_catalogo_nome').on('change', function() {
            const nomeSelecionado = $(this).val();
            // Busca a tag <option> com o nome que foi selecionado
            const selectedOption = $('#lista_vacinas option[value="' + nomeSelecionado + '"]');
            
            if (selectedOption.length > 0) {
                // Se encontrou no catálogo:
                $('#vacina_catalogo_id').val(selectedOption.data('id'));
                const validadeMeses = parseInt(selectedOption.data('validade')) || 0;
                
                if (validadeMeses > 0) {
                    // Calcula a data de retorno e preenche o campo
                    $('#vacina_retorno_previsto').val(calcularDataRetorno(validadeMeses));
                } else {
                    $('#vacina_retorno_previsto').val('');
                }
            } else {
                // Se digitou uma vacina nova (não está no catálogo) ou limpou o campo:
                $('#vacina_catalogo_id').val(''); 
                $('#vacina_retorno_previsto').val('');
            }
            
            calcularTotal();
            validateForm(); 
        });
        
        // O retorno da vacina é a única data calculada, mas o usuário pode ajustá-la
        $('#vacina_retorno_previsto').on('change', validateForm);
        
        // Fim da Lógica Vacina
        
        // 1.3 Lógica Consulta
        $('#consulta_servico_id').on('change', function() {
            calcularTotal();
            validateForm();
        });

        /**
         * Busca em tempo real de clientes
         */
        $('#cliente_id').replaceWith(`
    <input type="text" class="form-control form-control-sm" id="cliente_nome_busca" list="lista_clientes" placeholder="Digite o nome do cliente...">
    <datalist id="lista_clientes"></datalist>
    <input type="hidden" id="cliente_id" name="cliente_id">
`);

        $(document).on('input', '#cliente_nome_busca', function() {
            const termo = $(this).val().trim();
            if (termo.length < 1) return;

            $.ajax({
                url: 'servicos_buscar_clientes.php',
                method: 'GET',
                data: {
                    q: termo
                },
                dataType: 'json',
                success: function(clientes) {
                    let options = '';
                    clientes.forEach(function(c) {
                        options += `<option value="${c.nome}" data-id="${c.id}">${c.nome}</option>`;
                    });
                    $('#lista_clientes').html(options);
                }
            });
        });

        // Atualiza o campo hidden com o ID selecionado
        $(document).on('change', '#cliente_nome_busca', function() {
            const selectedOption = $('#lista_clientes option[value="' + $(this).val() + '"]');
            if (selectedOption.length > 0) {
                $('#cliente_id').val(selectedOption.data('id'));
                selectedClienteId = parseInt(selectedOption.data('id'));
                loadPets(selectedClienteId);
            } else {
                $('#cliente_id').val('');
                selectedClienteId = null;
            }
            validateForm();
        });


        // 2. Seleção de Cliente (CHAMA AJAX)
        // Este bloco é mantido, mas é menos utilizado devido ao novo campo de busca
        $('#cliente_id').on('change', function() {
            selectedClienteId = $(this).val() !== '' ? parseInt($(this).val()) : null;
            selectedPetId = null;

            loadPets(selectedClienteId);

            validateForm();
        });

        // 3. Seleção de Pet
        $('#pet_id').on('change', function() {
            const $selectedOption = $(this).find('option:selected');

            selectedPetId = $(this).val() !== '' ? parseInt($(this).val()) : null;

            if (selectedPetId) {
                // Atualiza Porte e Espécie
                selectedPetPorte = $selectedOption.data('porte') || 'Pequeno';
                selectedPetEspecie = $selectedOption.data('especie') || '';
                // Atualiza o display com o porte e espécie
                $('#pet-porte-display').text(selectedPetPorte + (selectedPetEspecie ? ` (${selectedPetEspecie})` : ''));
            } else {
                selectedPetPorte = 'Pequeno';
                selectedPetEspecie = '';
                $('#pet-porte-display').text('Pequeno');
            }

            // Recalcula o total (se for banho/tosa) e revalida
            calcularTotal();
            validateForm();
        });

        // 4. Validação de Data, Hora e Funcionário (Validação Final)
        $('#data_agendamento, #hora_agendamento, #funcionario_id_banhotosa, #funcionario_id_vacina, #funcionario_id_consulta').on('change', validateForm);

        // 5. Tratamento do Submit do Formulário
        $('#agendamento-form').on('submit', function(e) {

            if (!validateForm()) {
                e.preventDefault();
                // Esta é a mensagem de alerta da sua imagem `tela.png`
                alert("Preencha todos os campos obrigatórios e selecione o serviço, cliente, pet e responsável antes de agendar.");
                return;
            }

        });

        // Botão de Limpar
        $('#reset-button').on('click', function() {
            if (confirm('Tem certeza que deseja limpar o formulário e começar um novo agendamento?')) {
                resetSteps(1);
            }
        });

        // Inicialização: Garante que os passos e o botão de submit estejam no estado inicial correto.
        resetSteps(1); // Inicia com o reset completo para garantir o estado inicial.
    });
</script>