<?php
// Arquivo: servicos_agendamentos_listar.php - Versão Modificada (Interface com JavaScript de Busca)
// Objetivo: Exibir a interface de busca e gerenciar as requisições AJAX para servicos_agendamentos_buscar_rapido.php.

// NOTA: Toda a lógica de CONSULTA PHP foi removida e movida para o arquivo
// servicos_agendamentos_buscar_rapido.php, que será chamado via AJAX.

// Variável para evitar erro de variável não definida no HTML
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
                            <option value="confirmado">Confirmado</option>
                            <option value="concluido">Concluído</option>
                            <option value="cancelado">Cancelado</option>
                        </select>
                    </div>

                    <div class="col-md-3">
                        <label for="ordenacao" class="form-label">Ordenar por</label>
                        <select class="form-select" id="ordenacao" name="ordenacao">
                            <option value="data_crescente">Data (Mais Antigo)</option>
                            <option value="data_decrescente">Data (Mais Recente)</option>
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
            <?php echo $conteudo_inicial; // Deve estar vazio ?>
        </div>
    </div>
</div>

<script>
// Arquivo: servicos_agendamentos_listar.php - Trecho <script> (Ajustado)

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
    // NOVO ESTADO: Rastrea se o botão Mostrar/Esconder está ativo (mostrando todos)
    let isShowingAll = false; 

    /**
     * Função principal para realizar a requisição AJAX para servicos_agendamentos_buscar_rapido.php.
     * @param {number} pagina_atual A página a ser carregada.
     * @param {boolean} listar_todos Se deve listar todos (true) ou usar os filtros (false).
     */
    function realizarBusca(pagina_atual = 1, listar_todos = false) {
        
        // Se estiver no modo listar_todos, apenas esconde a mensagem informativa.
        if (listar_todos) {
            $msgInformativa.hide(); 
        } else {
            // Se NÃO estiver em listar_todos, verifica se deve mostrar a mensagem informativa.
            const busca_termo = $campoBusca.val().trim();
            const status_filtro = $campoStatus.val();
            
            if (busca_termo.length === 0 && status_filtro === 'todos') {
                $msgInformativa.show(); 
                $container.html('<div class="alert alert-warning text-center">Nenhum termo de busca ou filtro de status aplicado.</div>').show();
                // Oculta o container e ajusta o botão se for um filtro vazio
                toggleContainerVisibility(false); 
                return; 
            } else {
                 $msgInformativa.hide(); 
            }
        }
        
        // Atualiza o estado
        isShowingAll = listar_todos;

        const busca_termo = $campoBusca.val().trim();
        const status_filtro = $campoStatus.val();
        const ordenacao = $campoOrdenacao.val();
        
        let url = 'servicos_agendamentos_buscar_rapido.php?limite=10&pagina_atual=' + pagina_atual;

        if (listar_todos) {
            url += '&listar_todos=true';
            // Quando listamos todos, podemos zerar os campos de filtro para coerência visual
            // Opcional: manter os campos para que o usuário veja a última busca
        } else {
            // Adiciona os filtros de busca apenas se não for para listar todos
            if (busca_termo.length > 0) url += '&busca=' + encodeURIComponent(busca_termo);
            if (status_filtro !== 'todos') url += '&status_filtro=' + encodeURIComponent(status_filtro);
        }
        
        // Adiciona a ordenação em ambos os casos
        url += '&ordenacao=' + encodeURIComponent(ordenacao);

        // Adiciona um efeito de carregamento
        $container.html('<div class="text-center p-5"><i class="fas fa-spinner fa-spin fa-3x"></i><p class="mt-2">Buscando agendamentos...</p></div>').show(); // Garante que mostre ao carregar
        
        // Requisição AJAX (GET)
        $.ajax({
            url: url,
            method: 'GET',
            success: function(response) {
                $container.html(response);
                // Garante que o container esteja visível e o botão ajustado após uma busca bem-sucedida
                toggleContainerVisibility(true); 
            },
            error: function() {
                $container.html('<div class="alert alert-danger">Erro de conexão com o servidor. Tente novamente.</div>');
                toggleContainerVisibility(true); // Manter visível para mostrar o erro
            }
        });
    }
    
    // 1. Busca em Tempo Real (usando 'keyup' e 'change' com Debounce)
    
    // Handler que dispara a busca filtrada (listar_todos = false)
    function triggerFilteredSearch() {
        // Assegura que o modo "Listar Todos" é desativado
        isShowingAll = false; 
        
        const busca_termo = $campoBusca.val().trim();
        
        // Lógica de debounce para busca por texto
        clearTimeout(timerBusca);
        timerBusca = setTimeout(function() {
            // Regra: exige 3 caracteres no termo de busca OU se o filtro de status/ordenação foi mudado.
            if (busca_termo.length > 0 && busca_termo.length < 3) {
                return; 
            }
            realizarBusca(1, false);
        }, 300); // 300ms de debounce
    }
    
    // Altera a busca quando o termo, status ou ordenação muda
    $campoBusca.on('keyup', triggerFilteredSearch);

    $campoStatus.on('change', function() {
        // Dispara a busca imediatamente ao mudar o status (sem debounce de texto)
        clearTimeout(timerBusca);
        realizarBusca(1, false);
    });
    
    $campoOrdenacao.on('change', function() {
        // Dispara a busca imediatamente ao mudar a ordenação
        clearTimeout(timerBusca);
        realizarBusca(1, false);
    });


    // 2. Busca Explícita (Submit do Formulário) - O botão 'Buscar'
    $form.on('submit', function(e) {
        e.preventDefault(); 
        clearTimeout(timerBusca); 
        realizarBusca(1, false); 
    });
    
    // 3. Botão "Limpar Filtros"
    $btnLimpar.on('click', function(e) {
        e.preventDefault();
        $campoBusca.val('');
        $campoStatus.val('todos');
        $campoOrdenacao.val('data_crescente');
        
        // Volta ao estado inicial (Mensagem informativa, container escondido, botão verde)
        clearTimeout(timerBusca);
        isShowingAll = false; // Garante que o estado seja de "filtro normal"
        
        $container.html('');
        $msgInformativa.show();
        toggleContainerVisibility(false); 
    });

    // 4. Botão "Mostrar/Esconder Agendamentos" (Toggle) - LOGICA PRINCIPAL AQUI
    $btnToggle.on('click', function() {
        // Verifica o estado atual do botão através da classe (se está mostrando)
        const isCurrentlyShowing = $btnToggle.hasClass('btn-danger');

        if (isCurrentlyShowing) {
            // Se está MOSTRANDO (vermelho), deve ESCONDER
            toggleContainerVisibility(false);
            $container.html('');
            $msgInformativa.show(); 
            // Não é mais "listar todos", o estado volta a ser filtrado (que no caso é o vazio)
            isShowingAll = false; 
        } else {
            // Se está ESCONDIDO (verde), deve MOSTRAR TODOS
            toggleContainerVisibility(true);
            isShowingAll = true; // Define o estado para listar todos
            realizarBusca(1, true); // Carrega a lista completa
            $msgInformativa.hide(); // Garante que a mensagem seja escondida
        }
    });
    
    /**
     * Controla a visibilidade do container de resultados e a aparência do botão de toggle.
     * @param {boolean} show Se deve mostrar (true) ou esconder (false).
     */
    function toggleContainerVisibility(show) {
        if (show) {
            $container.show();
            // A cor é invertida: se está mostrando, o botão deve ser Vermelho (para esconder)
            $btnToggle.removeClass('btn-success').addClass('btn-danger');
            $btnToggle.html('<i class="fas fa-eye-slash me-1"></i> Esconder Agendamentos');
        } else {
            $container.hide();
            // Se está escondido, o botão deve ser Verde (para mostrar)
            $btnToggle.removeClass('btn-danger').addClass('btn-success');
            $btnToggle.html('<i class="fas fa-eye me-1"></i> Mostrar Agendamentos');
        }
    }
    
    // 5. Paginação (Delegação de Eventos) 
    $container.on('click', '.btn-pagina-agendamento', function(e) {
        e.preventDefault();
        
        const pagina = $(this).data('pagina');
        // Agora, a flag listar_todos_flag deve vir do estado global `isShowingAll` ou do atributo data, se for o caso.
        // É mais seguro usar o atributo data que é gerado dinamicamente pelo PHP (parte 2)
        const listar_todos_flag = $(this).data('listar-todos') === true || $(this).data('listar-todos') === 'true'; 
        
        realizarBusca(pagina, listar_todos_flag);
    });

    // Estado inicial: Garante que o container esteja escondido e o botão verde
    toggleContainerVisibility(false);

});