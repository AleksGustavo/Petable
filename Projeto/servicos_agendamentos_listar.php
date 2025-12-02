<?php
// Arquivo de Listagem de Agendamentos (Ex: servicos_agendamentos_listar.php)

$conteudo_inicial = ''; 
?>

<div class="container mt-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <h2><i class="fas fa-clipboard-list me-2"></i> Listagem de Agendamentos </h2>
        
        <div>
            <a href="#" class="btn btn-success item-menu-ajax" data-pagina="servicos_agendar_banhotosa.php">
                <i class="fas fa-plus me-1"></i> Novo Agendamento
            </a>
        </div>
    </div>

    <div class="card mb-4 shadow-sm">
        <div class="card-header bg-primary text-white">
            <i class="fas fa-filter me-2"></i> Filtrar e Pesquisar Agendamentos
        </div>
        <div class="card-body">
            <form id="form-busca-agendamentos"> 
                <div class="row g-3 align-items-end">
                    <div class="col-md-6">
                        <label for="busca" class="form-label">Pesquisar por Nome</label>
                        <input type="text" class="form-control" id="busca" name="busca" 
                                placeholder="Pet, Cliente ou Serviço">
                    </div>
                    
                    <div class="col-md-3">
                        <label for="status_filtro" class="form-label">Status</label>
                        <select class="form-select" id="status_filtro" name="status_filtro">
                            <option value="todos">Todos</option>
                            <option value="agendado">Agendado</option>
                            <option value="atrasado">Atrasado</option> 
                            <option value="confirmado">Confirmado</option>
                            <option value="concluido">Concluído</option>
                            <option value="cancelado">Cancelado</option>
                        </select>
                    </div>

                    <div class="col-md-3">
                        <label for="ordenacao" class="form-label">Ordenar por</label>
                        <select class="form-select" id="ordenacao" name="ordenacao">
                            <option value="id_decrescente">Criação (Mais Recente)</option> 
                            <option value="data_crescente" selected>Data Agendada (Mais Antigo)</option>
                            <option value="data_decrescente">Data Agendada (Mais Recente)</option>
                            <option value="cliente">Cliente (A-Z)</option>
                            <option value="pet">Pet (A-Z)</option>
                        </select>
                    </div>
                </div>

                <div class="mt-3 d-flex flex-column flex-md-row gap-2">
                    <button type="submit" class="btn btn-primary w-100 w-md-auto">
                        <i class="fas fa-search me-1"></i> Buscar Agendamentos
                    </button>
                    
                    <button type="button" id="btn-limpar-agendamentos" class="btn btn-secondary w-100 w-md-auto">
                        <i class="fas fa-sync-alt me-1"></i> Limpar Filtros
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

    <div id="resultado-busca-agendamentos" class="mt-4">
        
        <div class="alert alert-info" id="msg-informativa">
            Clique em "Mostrar Agendamentos" para listar todos, ou use a busca para resultados específicos.
        </div>
        
        <div id="tabela-agendamentos-container" style="display: none;">
            <?php echo $conteudo_inicial; ?>
        </div>
    </div>
</div>

<script>

$(document).ready(function() {
    
    const $container = $('#tabela-agendamentos-container');
    const $msgInformativa = $('#msg-informativa');
    const $form = $('#form-busca-agendamentos');
    const $campoBusca = $('#busca');
    const $campoStatus = $('#status_filtro');
    const $campoOrdenacao = $('#ordenacao');
    const $btnToggle = $('#btn-toggle-agendamentos');
    const $btnLimpar = $('#btn-limpar-agendamentos');
    
    let timerBusca = null; 
    let isShowingAll = false; 

    function toggleContainerVisibility(show) {
        if (show) {
            $container.show();
            $btnToggle.removeClass('btn-success').addClass('btn-danger');
            $btnToggle.html('<i class="fas fa-eye-slash me-1"></i> Esconder Agendamentos');
            $msgInformativa.hide();
        } else {
            $container.hide();
            $btnToggle.removeClass('btn-danger').addClass('btn-success');
            $btnToggle.html('<i class="fas fa-eye me-1"></i> Mostrar Agendamentos');
        }
    }

    function realizarBusca(pagina_atual = 1, listar_todos = false) {
        
        isShowingAll = listar_todos;

        const busca_termo = $campoBusca.val().trim();
        const status_filtro = $campoStatus.val();
        let ordenacao = $campoOrdenacao.val(); // Use 'let' para poder mudar

        // 🎯 LÓGICA DE CORREÇÃO (Item 1 e 2): Ordenação "Pilha" e "Mais Próxima"
        if (listar_todos) {
            // Prioriza data de agendamento mais próxima ou, se igual, o último criado.
            // Para isso, precisamos de um valor que o PHP no 'servicos_agendamentos_buscar_rapido.php' entenda.
            // Usamos 'data_proxima' para priorizar a data e 'id_decrescente' para ordem de criação
            ordenacao = 'data_proxima'; 
            
            // Se você quiser APENAS a ordem de criação, use:
            // ordenacao = 'id_decrescente';
        }
        
        // Se a busca for vazia e não for para listar todos, esconde
        if (!listar_todos && busca_termo.length === 0 && status_filtro === 'todos') {
            $msgInformativa.show(); 
            $container.html('');
            toggleContainerVisibility(false); 
            return; 
        } else {
            $msgInformativa.hide(); 
        }
        
        // Adicionando um parâmetro para tratar a ordenação e garantir a não duplicação (Item 3)
        // A lógica de remoção de duplicação deve ser implementada no PHP (servicos_agendamentos_buscar_rapido.php)
        // usando GROUP BY ou DISTINCT na ID principal do agendamento.
        let url = 'servicos_agendamentos_buscar_rapido.php?limite=10&pagina_atual=' + pagina_atual;

        if (listar_todos) {
            url += '&listar_todos=true';
        } else {
            if (busca_termo.length > 0) url += '&busca=' + encodeURIComponent(busca_termo);
            if (status_filtro !== 'todos') url += '&status_filtro=' + encodeURIComponent(status_filtro);
        }
        
        // A ordenação é enviada, seja a selecionada ou a especial 'data_proxima'
        url += '&ordenacao=' + encodeURIComponent(ordenacao);

        $container.html('<div class="text-center p-5"><i class="fas fa-spinner fa-spin fa-3x"></i><p class="mt-2">Buscando agendamentos...</p></div>').show(); 
        toggleContainerVisibility(true);
        
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
    
    function triggerFilteredSearch() {
        isShowingAll = false; 
        
        const busca_termo = $campoBusca.val().trim();
        
        clearTimeout(timerBusca);
        timerBusca = setTimeout(function() {
            if (busca_termo.length > 0 && busca_termo.length < 3) {
                // Se a busca for por termo, mas não atingir 3 caracteres, não faz nada
                return; 
            }
            realizarBusca(1, false);
        }, 300); 
    }
    
    // --- 1. Handlers de Filtro e Busca ---

    $campoBusca.on('keyup', triggerFilteredSearch);

    $campoStatus.on('change', function() {
        clearTimeout(timerBusca);
        realizarBusca(1, isShowingAll); 
    });
    
    $campoOrdenacao.on('change', function() {
        clearTimeout(timerBusca);
        // Quando o usuário muda a ordenação manualmente, ele desativa o modo 'listar_todos' especial
        realizarBusca(1, isShowingAll); 
    });


    $form.on('submit', function(e) {
        e.preventDefault(); 
        clearTimeout(timerBusca); 
        realizarBusca(1, false); 
    });
    
    $btnLimpar.on('click', function(e) {
        e.preventDefault();
        $campoBusca.val('');
        $campoStatus.val('todos');
        // Reseta para a nova opção de ordenação padrão
        $campoOrdenacao.val('id_decrescente'); 
        
        clearTimeout(timerBusca);
        isShowingAll = false; 
        
        $container.html('');
        $msgInformativa.show();
        toggleContainerVisibility(false); 
    });

    $btnToggle.on('click', function() {
        const isCurrentlyShowing = $btnToggle.hasClass('btn-danger');

        if (isCurrentlyShowing) {
            // Esconder
            toggleContainerVisibility(false);
            $container.html('');
            $msgInformativa.show(); 
            isShowingAll = false; 
        } else {
            // Mostrar Todos: Realiza a busca com o modo 'listar_todos=true'
            realizarBusca(1, true); 
        }
    });
    
    // --- 2. Handler de Paginação (Delegado) ---
    $container.on('click', '.btn-pagina-agendamento', function(e) {
        e.preventDefault();
        
        const pagina = $(this).data('pagina');
        realizarBusca(pagina, isShowingAll); 
    });

    // --- 3. Handler de Processamento de Ações ---
    $container.on('click', '.btn-processar-agendamento', function(e) {
        e.preventDefault();
        
        const id_agendamento = $(this).data('id');
        const acao = $(this).data('acao');
        const $linha = $(this).closest('tr');
        
        let confirm_msg = '';
        let url_processamento = '';

        if (acao === 'concluir_status') {
            confirm_msg = "Tem certeza que deseja marcar este agendamento como CONCLUÍDO?";
            url_processamento = 'servicos_agendamento_processar.php?acao=concluir_status'; 
        } else if (acao === 'cancelar_status') {
            confirm_msg = "Tem certeza que deseja CANCELAR este agendamento?";
            url_processamento = 'servicos_agendamento_processar.php?acao=cancelar_status';
        } else if (acao === 'deletar') {
            confirm_msg = "ATENÇÃO: Deseja EXCLUIR permanentemente este agendamento?";
            url_processamento = 'servicos_agendamento_processar.php?acao=deletar';
        } else {
             return; 
        }

        if (confirm(confirm_msg)) {
            
            $.ajax({
                url: url_processamento,
                method: 'POST', 
                data: { id: id_agendamento },
                dataType: 'json', 
                beforeSend: function() {
                    $linha.css('opacity', '0.5'); 
                },
                success: function(response) {
                    if (response.sucesso) {
                        alert("Sucesso! Agendamento #" + id_agendamento + " processado.");
                        // Recarrega a lista para refletir a mudança no status
                        realizarBusca(1, isShowingAll); 
                    } else {
                        alert('Erro ao processar agendamento: ' + response.mensagem.replace(/<[^>]*>?/gm, '')); 
                        $linha.css('opacity', '1');
                    }
                },
                error: function(xhr, status, error) {
                    alert('Erro de comunicação com o servidor. Status: ' + status);
                    console.error("Erro AJAX:", xhr.responseText);
                    $linha.css('opacity', '1');
                }
            });
        }
    });

    // Estado inicial: Esconde a tabela
    toggleContainerVisibility(false);
});
</script>