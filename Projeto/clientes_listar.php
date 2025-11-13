<?php
// Arquivo: clientes_listar.php
// Objetivo: Exibir a interface de busca e gerenciar as requisições AJAX para clientes_buscar_rapido.php.

// --- Lógica Inicial PHP ---
// A lista de clientes deve começar vazia e oculta, portanto, removemos a lógica de carregamento inicial.
$conteudo_inicial = ''; 
?>

<div class="d-flex justify-content-between align-items-center mb-4">
    <h2><i class="fas fa-list me-2"></i> Lista de Clientes </h2>
    
    <div>
        <a href="#" class="btn btn-success item-menu-ajax" data-pagina="clientes_cadastro.php">
            <i class="fas fa-user-plus me-2"></i> Novo Cliente
        </a>
    </div>
</div>

<div class="card mb-4 shadow-sm">
    <div class="card-header bg-secondary text-white">
        <i class="fas fa-search me-2"></i> Pesquisar Cliente (Busca Permissiva)
    </div>
    <div class="card-body">
        <form id="form-busca-cliente-rapida"> 
            <!-- Layout consistente com produtos_listar.php: 3 colunas de 4/12 -->
            <div class="row g-3">
                <div class="col-md-4">
                    <label for="busca_id" class="form-label">Buscar por ID</label>
                    <input type="number" id="busca_id" name="busca_id" class="form-control" placeholder="Ex: 123">
                </div>
                <div class="col-md-4">
                    <label for="busca_cpf" class="form-label">Buscar por CPF</label>
                    <input type="text" id="busca_cpf" name="busca_cpf" class="form-control" placeholder="Ex: 123.456.789-00">
                </div>
                <div class="col-md-4">
                    <label for="busca_nome" class="form-label">Buscar por Nome</label>
                    <input type="text" id="busca_nome" name="busca_nome" class="form-control" placeholder="Nome completo ou parcial">
                </div>
            </div>

            <div class="mt-3 d-flex flex-column flex-md-row gap-2">
                <button type="submit" class="btn btn-primary w-100 w-md-auto">
                    <i class="fas fa-search me-1"></i> Buscar Clientes
                </button>
                
                <div class="d-flex flex-grow-1 justify-content-end">
                    <!-- Botão Mostrar/Esconder. Começa verde (Mostrar) -->
                    <button type="button" id="btn-toggle-clientes" class="btn btn-success flex-fill">
                        <i class="fas fa-eye me-1"></i> Mostrar Clientes
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>

<div id="resultado-busca-rapida" class="mt-4">
    
    <div class="alert alert-info" id="msg-informativa">
        Preencha um ou mais campos. A busca retornará clientes que correspondam a pelo menos uma das informações fornecidas (ID, CPF ou Nome).
    </div>
    
    <!-- O container começa escondido (style="display: none;") -->
    <div id="tabela-clientes-container" style="display: none;">
        <?php echo $conteudo_inicial; ?>
    </div>

</div>

<script>
$(document).ready(function() {
    
    const $container = $('#tabela-clientes-container');
    const $msgInformativa = $('#msg-informativa');
    const $form = $('#form-busca-cliente-rapida');
    const $camposBusca = $('#busca_id, #busca_cpf, #busca_nome');
    let timerBusca = null; 

    /**
     * Função principal para realizar a requisição AJAX para clientes_buscar_rapido.php.
     * @param {number} pagina_atual A página a ser carregada.
     * @param {boolean} listar_todos Se deve listar todos (true) ou usar os filtros (false).
     */
    function realizarBusca(pagina_atual = 1, listar_todos = false) {
        
        $msgInformativa.hide(); 

        const busca_id = $('#busca_id').val();
        const busca_cpf = $('#busca_cpf').val();
        const busca_nome = $('#busca_nome').val();
        
        let url = 'clientes_buscar_rapido.php?limite=10&pagina_atual=' + pagina_atual;

        if (listar_todos) {
            url += '&listar_todos=true';
        } else {
            // Adiciona os filtros de busca apenas se não for para listar todos
            if (busca_id.length > 0) url += '&busca_id=' + encodeURIComponent(busca_id);
            if (busca_cpf.length > 0) url += '&busca_cpf=' + encodeURIComponent(busca_cpf);
            if (busca_nome.length > 0) url += '&busca_nome=' + encodeURIComponent(busca_nome);
            
            // Se nenhum filtro estiver ativo, volta para "Listar Todos" (comportamento mantido para o botão de Pesquisar)
            if (busca_id.length === 0 && busca_cpf.length === 0 && busca_nome.length === 0) {
                 url = 'clientes_buscar_rapido.php?listar_todos=true&limite=10&pagina_atual=1';
                 $msgInformativa.show(); 
            }
        }

        // Adiciona um efeito de carregamento
        $container.html('<div class="text-center p-5"><i class="fas fa-spinner fa-spin fa-3x"></i><p class="mt-2">Buscando clientes...</p></div>');
        
        // Requisição AJAX (GET)
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
    
    // 1. Busca em Tempo Real (usando 'keyup' com Debounce)
    $camposBusca.on('keyup', function() {
        clearTimeout(timerBusca);
        timerBusca = setTimeout(function() {
            const busca_nome = $('#busca_nome').val();
            const busca_id = $('#busca_id').val();
            const busca_cpf = $('#busca_cpf').val();
            
            // Lógica de busca em tempo real: exige 3 caracteres para busca por NOME, mas busca imediatamente se ID ou CPF forem preenchidos.
            if (busca_nome.length > 0 && busca_id.length === 0 && busca_cpf.length === 0 && busca_nome.length < 3) {
                 return; 
            }
            realizarBusca(1, false);
            // Garante que o container esteja visível após uma busca em tempo real
            if ($container.is(':hidden')) {
                $container.show();
                const $btn = $('#btn-toggle-clientes');
                $btn.removeClass('btn-success').addClass('btn-danger');
                $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Clientes');
            }
        }, 300);
    });

    // 2. Busca Explícita (Submit do Formulário)
    $form.on('submit', function(e) {
        e.preventDefault(); 
        clearTimeout(timerBusca); 
        realizarBusca(1, false); 
        // Garante que o container esteja visível após uma busca explícita
        if ($container.is(':hidden')) {
            $container.show();
            const $btn = $('#btn-toggle-clientes');
            $btn.removeClass('btn-success').addClass('btn-danger');
            $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Clientes');
        }
    });

    // 3. Botão "Mostrar/Esconder Clientes" (Toggle)
    $('#btn-toggle-clientes').on('click', function() {
        
        const $btn = $(this);
        const isHidden = $container.is(':hidden');

        if (isHidden) {
            // Lógica de MOSTRAR
            $container.show();
            
            // Troca para vermelho/Esconder
            $btn.removeClass('btn-success').addClass('btn-danger');
            $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Clientes');
            
            // Se o conteúdo ainda estiver vazio, carrega a lista completa no primeiro clique
            if ($container.is(':empty') || $container.text().trim() === '') {
                realizarBusca(1, true); 
            }
            
        } else {
            // Lógica de ESCONDER
            $container.hide();
            
            // Troca para verde/Mostrar
            $btn.removeClass('btn-danger').addClass('btn-success');
            $btn.html('<i class="fas fa-eye me-1"></i> Mostrar Clientes');
        }
    });
    
    // 4. Paginação (Delegação de Eventos) - Inalterada
    $container.on('click', '.btn-pagina-cliente', function(e) {
        e.preventDefault();
        
        const pagina = $(this).data('pagina');
        const listar_todos_flag = $(this).data('listar-todos') === true; 
        
        realizarBusca(pagina, listar_todos_flag);
    });
});
</script>