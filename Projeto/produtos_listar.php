<?php
// Arquivo: produtos_listar.php
require_once 'conexao.php'; 

$erro_sql = null;
$produtos = []; 

$conteudo_inicial = ''; // Conteúdo carregado via AJAX
?>

<div class="d-flex justify-content-between align-items-center mb-4">
    <h2><i class="fas fa-boxes me-2"></i> Lista de Produtos </h2>
    
    <div>
        <!-- Botão Novo Produto -->
        <a href="#" class="btn btn-success item-menu-ajax" data-pagina="produtos_cadastro.php">
            <i class="fas fa-plus me-2"></i> Novo Produto
        </a>
    </div>
</div>

<div id="status-message-area">
    <?php if (isset($erro_sql)): ?>
        <div class="alert alert-danger"><?= $erro_sql ?></div>
    <?php endif; ?>
</div>

<div class="card mb-4 shadow-sm">
    <div class="card-header bg-secondary text-white">
        <i class="fas fa-search me-2"></i> Pesquisar Produto
    </div>
    <div class="card-body">
        <form id="form-busca-produto-rapida"> 
            <div class="row g-3">
                
                <div class="col-md-4">
                    <label for="busca_nome" class="form-label">Nome do Produto</label>
                    <input type="text" id="busca_nome" name="busca_nome" class="form-control" placeholder="Nome do produto">
                </div>
                
                <div class="col-md-4">
                    <label for="busca_categoria" class="form-label">Buscar por Categoria</label>
                    <input type="text" id="busca_categoria" name="busca_categoria" class="form-control" placeholder="Nome da Categoria">
                </div>
                
                <div class="col-md-4">
                    <label for="busca_fornecedor" class="form-label">Buscar por Fornecedor</label>
                    <input type="text" id="busca_fornecedor" name="busca_fornecedor" class="form-control" placeholder="Nome do Fornecedor">
                </div>
                
                <!-- Nova Opção de Ordenação -->
                <div class="col-md-4">
                    <label for="ordenacao" class="form-label">Ordenar por</label>
                    <select id="ordenacao" name="ordenacao" class="form-select">
                        <option value="nome">Nome (A-Z)</option>
                        <option value="id">ID (Crescente)</option>
                        <option value="fornecedor">Fornecedor (Nome Fantasia)</option>
                    </select>
                </div>
                
            </div>

            <div class="mt-3 d-flex flex-column flex-md-row gap-2">
                <button type="submit" class="btn btn-primary w-100 w-md-auto">
                    <i class="fas fa-search me-1"></i> Buscar Produtos
                </button>
                
                <div class="d-flex flex-grow-1 justify-content-end">
                    <!-- Botão Mostrar/Esconder Produtos (Toggle) -->
                    <button type="button" id="btn-toggle-produtos" class="btn btn-success flex-fill">
                        <i class="fas fa-eye me-1"></i> Mostrar Produtos
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>

<div id="resultado-busca-rapida" class="mt-4">
    <div class="alert alert-info" id="msg-informativa">
        Clique em "Mostrar Produtos" para listar todos (paginado em 10) ou preencha os campos para buscar.
    </div>

    <!-- Container para o resultado da busca (tabela) -->
    <div id="tabela-produtos-container" style="display: none;">
        <?php echo $conteudo_inicial; ?>
    </div>
</div>

<script>
$(document).ready(function() {
    
    // Variáveis de referência do DOM
    const $container = $('#tabela-produtos-container');
    const $msgInformativa = $('#msg-informativa');
    const $form = $('#form-busca-produto-rapida');
    const $camposBusca = $('#busca_nome, #busca_categoria, #busca_fornecedor');
    const $ordenacao = $('#ordenacao');
    let timerBusca = null; 

    // Função principal para realizar a requisição AJAX (Busca e Paginação)
    function realizarBusca(pagina_atual = 1, listar_todos = false) {
        
        $msgInformativa.hide(); 

        // Recupera valores dos filtros e ordenação
        const busca_nome = $('#busca_nome').val();
        const busca_categoria = $('#busca_categoria').val();
        const busca_fornecedor = $('#busca_fornecedor').val();
        const ordenacao = $ordenacao.val(); 
        
        // Define a URL base com limite e paginação (limite=10 é fixo)
        let url = 'produtos_buscar_rapido.php?limite=10&pagina_atual=' + pagina_atual;
        url += '&ordenacao=' + encodeURIComponent(ordenacao); // Adiciona o parâmetro de ordenação

        if (listar_todos) {
            url += '&listar_todos=true';
        } else {
            // Adiciona os filtros de busca
            if (busca_nome.length > 0) url += '&busca_nome=' + encodeURIComponent(busca_nome);
            if (busca_categoria.length > 0) url += '&busca_categoria=' + encodeURIComponent(busca_categoria);
            if (busca_fornecedor.length > 0) url += '&busca_fornecedor=' + encodeURIComponent(busca_fornecedor);
            
            // Se nenhum filtro estiver ativo, força a listagem completa (paginada)
            if (busca_nome.length === 0 && busca_categoria.length === 0 && busca_fornecedor.length === 0) {
                 url = 'produtos_buscar_rapido.php?listar_todos=true&limite=10&pagina_atual=1';
                 url += '&ordenacao=' + encodeURIComponent(ordenacao);
                 $msgInformativa.show(); 
            }
        }

        // Adiciona um efeito de carregamento visual
        $container.html('<div class="text-center p-5"><i class="fas fa-spinner fa-spin fa-3x"></i><p class="mt-2">Buscando produtos...</p></div>');
        
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
    
    // 1. Busca em Tempo Real e Ordenação (keyup/change com Debounce)
    $camposBusca.on('keyup', function() {
        clearTimeout(timerBusca);
        // Define o timer para executar a busca após 300ms de inatividade
        timerBusca = setTimeout(function() {
            realizarBusca(1, false);
            
            // Garante que o container esteja visível após uma busca
            if ($container.is(':hidden')) {
                $container.show();
                const $btn = $('#btn-toggle-produtos');
                $btn.removeClass('btn-success').addClass('btn-danger');
                $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Produtos');
            }
        }, 300); 
    });

    // A ordenação deve acionar a busca ao ser alterada
    $ordenacao.on('change', function() {
        realizarBusca(1, false);
        // Garante que o container esteja visível ao mudar a ordenação
        if ($container.is(':hidden')) {
            $container.show();
            const $btn = $('#btn-toggle-produtos');
            $btn.removeClass('btn-success').addClass('btn-danger');
            $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Produtos');
        }
    });

    // 2. Busca Explícita (Submit do Formulário)
    $form.on('submit', function(e) {
        e.preventDefault(); 
        clearTimeout(timerBusca); 
        realizarBusca(1, false); 
        
        // Garante que o container esteja visível após uma busca explícita
        if ($container.is(':hidden')) {
            $container.show();
            const $btn = $('#btn-toggle-produtos');
            $btn.removeClass('btn-success').addClass('btn-danger');
            $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Produtos');
        }
    });

    // 3. Botão "Mostrar/Esconder Produtos" (Toggle)
    $('#btn-toggle-produtos').on('click', function() {
        
        const $btn = $(this);
        const isHidden = $container.is(':hidden');

        if (isHidden) {
            // Lógica de MOSTRAR: carrega a primeira página paginada em 10
            $container.show();
            
            // Atualiza o botão para "Esconder"
            $btn.removeClass('btn-success').addClass('btn-danger');
            $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Produtos');
            
            // Carrega a primeira página da lista completa (limite=10)
            realizarBusca(1, true); 
            
        } else {
            // Lógica de ESCONDER
            $container.hide();
            
            // Atualiza o botão para "Mostrar"
            $btn.removeClass('btn-danger').addClass('btn-success');
            $btn.html('<i class="fas fa-eye me-1"></i> Mostrar Produtos');
        }
    });
    
    // 4. Paginação (Delegação de Eventos)
    $container.on('click', '.btn-pagina-produto', function(e) {
        e.preventDefault();
        
        const pagina = $(this).data('pagina');
        const listar_todos_flag = $(this).data('listar-todos') === true; 
        
        realizarBusca(pagina, listar_todos_flag);
    });
});
</script>