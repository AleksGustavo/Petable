<?php
require_once 'conexao.php'; 

$erro_sql = null;
$produtos = []; 
$conteudo_inicial = '';
?>

<div class="d-flex justify-content-between align-items-center mb-4">
    <h2><i class="fas fa-boxes me-2"></i> Lista de Produtos </h2>
    
    <div>
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

    <div id="tabela-produtos-container" style="display: none;">
        <?php echo $conteudo_inicial; ?>
    </div>
</div>

<script>
$(document).ready(function() {
    
    const $container = $('#tabela-produtos-container');
    const $msgInformativa = $('#msg-informativa');
    const $form = $('#form-busca-produto-rapida');
    const $camposBusca = $('#busca_nome, #busca_categoria, #busca_fornecedor');
    const $ordenacao = $('#ordenacao');
    let timerBusca = null; 

    function realizarBusca(pagina_atual = 1, listar_todos = false) {
        
        $msgInformativa.hide(); 

        const busca_nome = $('#busca_nome').val();
        const busca_categoria = $('#busca_categoria').val();
        const busca_fornecedor = $('#busca_fornecedor').val();
        const ordenacao = $ordenacao.val(); 
        
        let url = 'produtos_buscar_rapido.php?limite=10&pagina_atual=' + pagina_atual;
        url += '&ordenacao=' + encodeURIComponent(ordenacao);

        if (listar_todos) {
            url += '&listar_todos=true';
        } else {
            if (busca_nome.length > 0) url += '&busca_nome=' + encodeURIComponent(busca_nome);
            if (busca_categoria.length > 0) url += '&busca_categoria=' + encodeURIComponent(busca_categoria);
            if (busca_fornecedor.length > 0) url += '&busca_fornecedor=' + encodeURIComponent(busca_fornecedor);
            
            if (busca_nome.length === 0 && busca_categoria.length === 0 && busca_fornecedor.length === 0) {
                 url = 'produtos_buscar_rapido.php?listar_todos=true&limite=10&pagina_atual=1';
                 url += '&ordenacao=' + encodeURIComponent(ordenacao);
                 $msgInformativa.show(); 
            }
        }

        $container.html('<div class="text-center p-5"><i class="fas fa-spinner fa-spin fa-3x"></i><p class="mt-2">Buscando produtos...</p></div>');

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
    
    $camposBusca.on('keyup', function() {
        clearTimeout(timerBusca);
        timerBusca = setTimeout(function() {
            realizarBusca(1, false);
            if ($container.is(':hidden')) {
                $container.show();
                const $btn = $('#btn-toggle-produtos');
                $btn.removeClass('btn-success').addClass('btn-danger');
                $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Produtos');
            }
        }, 300); 
    });

    $ordenacao.on('change', function() {
        realizarBusca(1, false);
        if ($container.is(':hidden')) {
            $container.show();
            const $btn = $('#btn-toggle-produtos');
            $btn.removeClass('btn-success').addClass('btn-danger');
            $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Produtos');
        }
    });

    $form.on('submit', function(e) {
        e.preventDefault(); 
        clearTimeout(timerBusca); 
        realizarBusca(1, false); 
        
        if ($container.is(':hidden')) {
            $container.show();
            const $btn = $('#btn-toggle-produtos');
            $btn.removeClass('btn-success').addClass('btn-danger');
            $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Produtos');
        }
    });

    $('#btn-toggle-produtos').on('click', function() {
        
        const $btn = $(this);
        const isHidden = $container.is(':hidden');

        if (isHidden) {
            $container.show();
            $btn.removeClass('btn-success').addClass('btn-danger');
            $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Produtos');
            realizarBusca(1, true); 
        } else {
            $container.hide();
            $btn.removeClass('btn-danger').addClass('btn-success');
            $btn.html('<i class="fas fa-eye me-1"></i> Mostrar Produtos');
        }
    });
    
    $container.on('click', '.btn-pagina-produto', function(e) {
        e.preventDefault();
        
        const pagina = $(this).data('pagina');
        const listar_todos_flag = $(this).data('listar-todos') === true; 
        
        realizarBusca(pagina, listar_todos_flag);
    });
});
</script>
