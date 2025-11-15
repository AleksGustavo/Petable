<?php
// Arquivo: servicos_agendamentos_listar.php
// Contém a interface HTML e o JavaScript para busca e paginação AJAX.

require_once 'conexao.php'; 

<<<<<<< HEAD
$ID_SERVICO_VACINA = 6; 

// ==============================================================================
// 1. Lógica de Pesquisa e Filtro (CORRIGIDA COM GROUP BY)
// ==============================================================================
$termo_busca = $_GET['busca'] ?? '';
$filtro_status = $_GET['status_filtro'] ?? 'todos';

// Removido o DISTINCT e o GROUP BY será adicionado no final
$sql = "SELECT 
            a.id AS agendamento_id, 
            a.data_agendamento, 
            a.status, 
            a.servico_id,     
            a.pet_id,         
            c.nome AS cliente_nome, 
            p.nome AS pet_nome,
            s.nome AS servico_nome 
        FROM 
            agendamento a 
        JOIN 
            pet p ON a.pet_id = p.id
        JOIN 
            cliente c ON p.cliente_id = c.id
        JOIN
            servico s ON a.servico_id = s.id
        WHERE 1=1 "; 
$params_types = ''; 
$params = [];

// Filtro por termo de busca (Cliente, Pet ou Serviço)
if (!empty($termo_busca)) {
    $sql .= " AND (c.nome LIKE ? OR p.nome LIKE ? OR s.nome LIKE ?) ";
    $like = '%' . $termo_busca . '%';
    $params_types .= 'sss';
    $params[] = $like;
    $params[] = $like;
    $params[] = $like;
}

// Filtro por Status
if ($filtro_status !== 'todos' && in_array($filtro_status, ['agendado', 'confirmado', 'concluido', 'cancelado'])) {
    $sql .= " AND a.status = ? ";
    $params_types .= 's';
    $params[] = $filtro_status;
}

// Adiciona o GROUP BY para evitar duplicação no resultado final da agregação
$sql .= " GROUP BY a.id, a.data_agendamento, a.status, a.servico_id, a.pet_id, c.nome, p.nome, s.nome";

$sql .= " ORDER BY a.data_agendamento ASC"; 

$agendamentos = [];
$erro_sql = ''; 

// Sua conexão usa mysqli, vamos adaptar a execução para garantir
if (isset($conexao) && $conexao) {
    try {
        $stmt = mysqli_prepare($conexao, $sql);
        
        if (!empty($params)) {
            // Se você está usando PHP < 8.1, pode precisar de uma chamada dinâmica para mysqli_stmt_bind_param
            // CORREÇÃO: Usando o array_merge correto para a chamada dinâmica
            $bind_params = array_merge([$stmt, $params_types], $params);
            call_user_func_array('mysqli_stmt_bind_param', array_ref($bind_params));
        }

        mysqli_stmt_execute($stmt);
        $result = mysqli_stmt_get_result($stmt);
        
        if ($result) {
            $agendamentos = mysqli_fetch_all($result, MYSQLI_ASSOC);
        } else {
            $erro_sql = 'Erro ao executar a consulta: ' . mysqli_error($conexao);
        }
    } catch (\Exception $e) {
        $erro_sql = 'Erro na preparação da consulta: ' . $e->getMessage();
    }
} else {
    $erro_sql = 'Erro crítico: Conexão mysqli indisponível.';
}

// Função auxiliar para referências (necessária para call_user_func_array com bind_param no PHP antigo)
if (!function_exists('array_ref')) {
    function array_ref(&$arr) {
        $refs = [];
        foreach ($arr as $key => $value)
            $refs[$key] = &$arr[$key];
        return $refs;
    }
}


// Função para formatar o status com cor (Usada na listagem)
function formatar_status($status) {
    $classe = match ($status) {
        'agendado' => 'badge bg-primary',
        'confirmado' => 'badge bg-info',
        'concluido' => 'badge bg-success',
        'cancelado' => 'badge bg-danger',
        default => 'badge bg-secondary',
    };
    return "<span class='{$classe}'>" . ucfirst($status) . "</span>";
}
=======
$conteudo_inicial = ''; // Conteúdo inicial (opcionalmente vazio, carregado via JS)
>>>>>>> b9ea0a168763b9193e16431b87dd71ac1eeaff7a
?>

<div class="d-flex justify-content-between align-items-center mb-4">
    <h2><i class="fas fa-clipboard-list me-2"></i> Listagem de Agendamentos</h2>
    
    <div>
        <a href="#" class="btn btn-success item-menu-ajax" data-pagina="servicos_agendar_banhotosa.php">
            <i class="fas fa-plus me-2"></i> Novo Agendamento
        </a>
    </div>
</div>

<div id="status-message-area">
    </div>

<div class="card mb-4 shadow-sm">
    <div class="card-header bg-secondary text-white">
        <i class="fas fa-search me-2"></i> Pesquisar Agendamento
    </div>
    <div class="card-body">
        <form id="form-busca-agendamento-rapida"> 
            <div class="row g-3">
                
                <div class="col-md-5">
                    <label for="busca" class="form-label">Pesquisar</label>
                    <input type="text" id="busca" name="busca" class="form-control" placeholder="Pet, Cliente ou Serviço">
                </div>
                
                <div class="col-md-3">
                    <label for="status_filtro" class="form-label">Status</label>
                    <select class="form-select" id="status_filtro" name="status_filtro">
                        <option value="todos">Todos</option>
                        <option value="agendado">Agendado</option>
                        <option value="confirmado">Confirmado</option>
                        <option value="concluido">Concluído</option>
                        <option value="cancelado">Cancelado</option>
                    </select>
                </div>
                
                <div class="col-md-4">
                    <label for="ordenacao" class="form-label">Ordenar por</label>
                    <select id="ordenacao" name="ordenacao" class="form-select">
                        <option value="data_crescente">Data (Mais Antiga)</option>
                        <option value="data_decrescente">Data (Mais Recente)</option>
                        <option value="cliente">Cliente (Nome)</option>
                        <option value="pet">Pet (Nome)</option>
                    </select>
                </div>
                
            </div>

            <div class="mt-3 d-flex flex-column flex-md-row gap-2">
                <button type="submit" class="btn btn-primary w-100 w-md-auto">
                    <i class="fas fa-search me-1"></i> Buscar Agendamentos
                </button>
                
                <div class="d-flex flex-grow-1 justify-content-end">
                    <button type="button" id="btn-toggle-agendamentos" class="btn btn-success flex-fill">
                        <i class="fas fa-eye me-1"></i> Mostrar Agendamentos
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>

<div id="resultado-busca-rapida" class="mt-4">
    <div class="alert alert-info" id="msg-informativa">
        Clique em "Mostrar Agendamentos" para listar todos (paginado em 10) ou preencha os campos para buscar.
    </div>

    <div id="tabela-agendamentos-container" style="display: none;">
        <?php echo $conteudo_inicial; ?>
    </div>
</div>


<script>
$(document).ready(function() {
    
    // =========================================================================
    // VARIÁVEIS DE CONFIGURAÇÃO E REFERÊNCIA DO DOM
    // =========================================================================
    const $container = $('#tabela-agendamentos-container');
    const $msgInformativa = $('#msg-informativa');
    const $form = $('#form-busca-agendamento-rapida');
    const $campoBusca = $('#busca');
    const $statusFiltro = $('#status_filtro');
    const $ordenacao = $('#ordenacao');
    const $btnToggle = $('#btn-toggle-agendamentos');
    let timerBusca = null; 

    // =========================================================================
    // FUNÇÃO PRINCIPAL: REALIZAR REQUISIÇÃO AJAX (Busca, Filtro e Paginação)
    // =========================================================================
    function realizarBusca(pagina_atual = 1, listar_todos = false) {
        
        // Esconde a mensagem informativa durante a busca
        $msgInformativa.hide(); 

        // 1. Coleta e codificação dos valores do formulário
        const busca = $campoBusca.val().trim();
        const status_filtro = $statusFiltro.val();
        const ordenacao = $ordenacao.val(); 

        // 2. Montagem da URL base (limite=10 é fixo)
        let url = 'servicos_agendamento_buscar_rapido.php?limite=10&pagina_atual=' + pagina_atual;
        url += '&ordenacao=' + encodeURIComponent(ordenacao);

        // 3. Adiciona os parâmetros de filtro (Busca ou Listar Todos)
        if (listar_todos) {
            url += '&listar_todos=true';
        } else {
            let temFiltroAtivo = false;
            
            if (busca.length > 0) {
                url += '&busca=' + encodeURIComponent(busca);
                temFiltroAtivo = true;
            }
            if (status_filtro !== 'todos') {
                url += '&status_filtro=' + encodeURIComponent(status_filtro);
                temFiltroAtivo = true;
            }
            
            // Se nenhum filtro estiver ativo, força a listagem completa (paginada) 
            // e mostra a mensagem informativa novamente
            if (!temFiltroAtivo) {
                 url = 'servicos_agendamento_buscar_rapido.php?listar_todos=true&limite=10&pagina_atual=1';
                 url += '&ordenacao=' + encodeURIComponent(ordenacao);
                 $msgInformativa.show(); 
            }
        }

        // 4. Efeito de carregamento e Requisição AJAX (GET)
        $container.html('<div class="text-center p-5"><i class="fas fa-spinner fa-spin fa-3x"></i><p class="mt-2">Buscando agendamentos...</p></div>');
        
        $.ajax({
            url: url,
            method: 'GET',
            success: function(response) {
                $container.html(response);
            },
            error: function() {
                $container.html('<div class="alert alert-danger">Erro de conexão com o servidor. Tente novamente.</div>');
            }
        });
    }
    
    // Função auxiliar para garantir que o container esteja visível e o botão atualizado
    function mostrarContainer() {
        if ($container.is(':hidden')) {
            $container.show();
            $btnToggle.removeClass('btn-success').addClass('btn-danger');
            $btnToggle.html('<i class="fas fa-eye-slash me-1"></i> Esconder Agendamentos');
        }
    }


    // =========================================================================
    // EVENTOS DE INTERAÇÃO (Busca em Tempo Real, Submit e Toggle)
    // =========================================================================

    // 1. Busca em Tempo Real (keyup no campo de busca com Debounce)
    $campoBusca.on('keyup', function() {
        clearTimeout(timerBusca);
        // Executa a busca após 300ms de inatividade
        timerBusca = setTimeout(function() {
            realizarBusca(1, false);
            mostrarContainer();
        }, 300); 
    });

    // 2. Filtro de Status e Ordenação (change no select)
    $statusFiltro.on('change', function() {
        realizarBusca(1, false);
        mostrarContainer();
    });
    
    $ordenacao.on('change', function() {
        realizarBusca(1, false);
        mostrarContainer();
    });


    // 3. Busca Explícita (Submit do Formulário)
    $form.on('submit', function(e) {
        e.preventDefault(); 
        clearTimeout(timerBusca); 
        realizarBusca(1, false); 
        mostrarContainer();
    });


    // 4. Botão "Mostrar/Esconder Agendamentos" (Toggle)
    $btnToggle.on('click', function() {
        const isHidden = $container.is(':hidden');

        if (isHidden) {
            // Lógica de MOSTRAR: Carrega a primeira página da lista completa
            mostrarContainer(); 
            realizarBusca(1, true); 
            
        } else {
            // Lógica de ESCONDER
            $container.hide();
            $btnToggle.removeClass('btn-danger').addClass('btn-success');
            $btnToggle.html('<i class="fas fa-eye me-1"></i> Mostrar Agendamentos');
        }
    });
    
    
    // 5. Paginação (Delegação de Eventos para botões gerados dinamicamente)
    // Os botões de paginação devem ter a classe .btn-pagina-agendamento (definida no PHP do backend)
    $container.on('click', '.btn-pagina-agendamento', function(e) {
        e.preventDefault();
        
        const pagina = $(this).data('pagina');
        // Converte a string 'true'/'false' em booleano
        const listar_todos_flag = $(this).data('listar-todos') === true || $(this).data('listar-todos') === 'true'; 
        
        realizarBusca(pagina, listar_todos_flag);
    });

    
    // 6. Lógica de "Ocultar Concluídos" (Não implementado, apenas o HTML do botão removido)
    // O status-filtro já gerencia isso. Você pode adicionar um evento para isso aqui, se quiser.
    // Exemplo: $('#toggle-concluidos').on('click', function() { /* ... */ });


    // 7. Lógica de Ações (Editar, Concluir, Cancelar, Excluir - delegação de evento)
    // Presume-se que um arquivo 'servicos_agendamento_processar.php' ou similar
    // fará o processamento dessas ações e retornará uma mensagem de status.
    $container.on('click', '.btn-processar-agendamento', function() {
        // Lógica de AJAX para processar a ação (Concluir, Excluir, Cancelar)
        // Após o sucesso, você deve chamar `realizarBusca(pagina_atual, listar_todos_flag)`
        // para recarregar a tabela e refletir a mudança.
        const id = $(this).data('id');
        const acao = $(this).data('acao');
        
        // Exemplo: Confirmar ação antes de executar
        if (confirm(`Tem certeza que deseja ${acao === 'deletar' ? 'EXCLUIR PERMANENTEMENTE' : acao.toUpperCase()} o agendamento ${id}?`)) {
            $.ajax({
                url: 'servicos_agendamento_processar.php', // Crie este arquivo, se não existir
                method: 'POST',
                data: { id: id, acao: acao },
                success: function(response) {
                    $('#status-message-area').html(response.mensagem); // Presume que o backend retorna JSON com 'mensagem'
                    
                    // Recarrega a página atual para refletir a alteração
                    // É preciso saber a página atual e o status de listagem atual
                    // Para simplificar, vamos recarregar a lista completa, se estiver visível.
                    if ($container.is(':visible')) {
                        // Encontra a página e status de listagem atuais do último request bem sucedido
                        // (Isso é uma simplificação, o ideal seria guardar o último estado)
                        realizarBusca(1, true); // Recarrega do início
                    }
                },
                error: function() {
                    $('#status-message-area').html('<div class="alert alert-danger">Erro ao processar a ação.</div>');
                }
            });
        }
    });
});
</script>