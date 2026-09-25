<?php
// Arquivo: clientes_listar.php


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

    
    function realizarBusca(pagina_atual = 1, listar_todos = false) {
        
        $msgInformativa.hide(); 

        const busca_id = $('#busca_id').val();
        const busca_cpf = $('#busca_cpf').val();
        const busca_nome = $('#busca_nome').val();
        
        let url = 'clientes_buscar_rapido.php?limite=10&pagina_atual=' + pagina_atual;

        if (listar_todos) {
            url += '&listar_todos=true';
        } else {
            
            if (busca_id.length > 0) url += '&busca_id=' + encodeURIComponent(busca_id);
            if (busca_cpf.length > 0) url += '&busca_cpf=' + encodeURIComponent(busca_cpf);
            if (busca_nome.length > 0) url += '&busca_nome=' + encodeURIComponent(busca_nome);
            
            
            if (busca_id.length === 0 && busca_cpf.length === 0 && busca_nome.length === 0) {
                 url = 'clientes_buscar_rapido.php?listar_todos=true&limite=10&pagina_atual=1';
                 $msgInformativa.show(); 
            }
        }

        
        $container.html('<div class="text-center p-5"><i class="fas fa-spinner fa-spin fa-3x"></i><p class="mt-2">Buscando clientes...</p></div>');
        
        
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
            
            
            if (busca_nome.length > 0 && busca_id.length === 0 && busca_cpf.length === 0 && busca_nome.length < 3) {
                 return; 
            }
            realizarBusca(1, false);
            
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
            
            $container.show();
            
            
            $btn.removeClass('btn-success').addClass('btn-danger');
            $btn.html('<i class="fas fa-eye-slash me-1"></i> Esconder Clientes');
            
            
            if ($container.is(':empty') || $container.text().trim() === '') {
                realizarBusca(1, true); 
            }
            
        } else {
            
            $container.hide();
            
            
            $btn.removeClass('btn-danger').addClass('btn-success');
            $btn.html('<i class="fas fa-eye me-1"></i> Mostrar Clientes');
        }
    });
    
    // 4. Paginação (Delegação de Eventos)
    $container.on('click', '.btn-pagina-cliente', function(e) {
        e.preventDefault();
        
        const pagina = $(this).data('pagina');
        const listar_todos_flag = $(this).data('listar-todos') === true; 
        
        realizarBusca(pagina, listar_todos_flag);
    });
    
    // 5. NOVO CÓDIGO: Ação de Exclusão (Delegação de Eventos)
    $container.on('click', '.btn-excluir-cliente', function(e) {
        e.preventDefault(); 
        
        
        const clienteId = $(this).data('id');
        const $linha = $(this).closest('tr');
        
        
        if (!confirm('Tem certeza que deseja EXCLUIR o Cliente ID ' + clienteId + '? Esta ação irá inativar o cliente (Soft Delete)!')) {
            return; 
        }

        
        const $btn = $(this);
        const htmlOriginal = $btn.html();
        $btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i>');

        
        $.ajax({
            url: 'clientes_excluir.php', 
            method: 'POST',
            
            data: { id_cliente: clienteId }, 
            dataType: 'json',
            success: function(response) {
                if (response.success) {
                    
                    $linha.fadeOut(500, function() {
                        $(this).remove();
                        alert('✅ Sucesso: ' + response.message);
                        
                        
                        const paginaAtual = $('.btn-pagina-cliente.active').data('pagina') || 1;
                        const listarTodos = $('.btn-pagina-cliente.active').data('listar-todos') || false;

                        realizarBusca(paginaAtual, listarTodos); 
                    });
                } else {
                    
                    alert('❌ Erro: ' + (response.message || 'Erro desconhecido ao excluir.'));
                    $btn.prop('disabled', false).html(htmlOriginal);
                }
            },
            error: function(xhr, status, error) {
                
                alert('❌ Erro de conexão com o servidor ao tentar excluir. Tente novamente.');
                $btn.prop('disabled', false).html(htmlOriginal);
            }
        });
    });
});
</script>