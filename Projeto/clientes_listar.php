<?php
// Arquivo: clientes_listar.php
// Este arquivo contém o HTML e o JavaScript que envia os dados de busca via AJAX.
// Inclua aqui seu código PHP que precede o HTML, se houver.
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
                    <label for="busca_cpf" class="form-label">Buscar por CPF (Apenas números)</label>
                    <input type="tel" id="busca_cpf" name="busca_cpf" class="form-control input-numbers-only" maxlength="11" placeholder="Ex: 12345678900">
                </div>
                <div class="col-md-4">
                    <label for="busca_nome" class="form-label">Buscar por Nome</label>
                    <input type="text" id="busca_nome" name="busca_nome" class="form-control" placeholder="Nome completo ou parcial">
                </div>
            </div>

            <div class="mt-3 d-flex flex-column flex-md-row gap-2">
                <button type="submit" class="btn btn-primary w-100 w-md-auto" id="btn-buscar">
                    <i class="fas fa-search me-1"></i> Buscar Clientes
                </button>
                
                <div class="d-flex gap-2 flex-grow-1">
                    <button type="button" id="btn-listar-todos-clientes" class="btn btn-info flex-fill text-white">
                        <i class="fas fa-users me-1"></i> Listar Todos
                    </button>
                    
                    <button type="button" id="btn-esconder-clientes" class="btn btn-light flex-fill">
                        <i class="fas fa-eye-slash me-1"></i> Esconder
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>

<div id="resultado-busca-rapida" class="mt-4">
    
    <div class="alert alert-info">
        Preencha um ou mais campos. A busca retornará clientes que correspondam a pelo menos uma das informações fornecidas (ID, CPF ou Nome).
    </div>

</div>

<script>
    document.addEventListener('DOMContentLoaded', function() {
        // ... (código mantido) ...

        // 2. Lógica de submissão do formulário via AJAX/Fetch
        formBusca.addEventListener('submit', function(e) {
            e.preventDefault();
            
            const formData = new FormData(this);
            
            // GARANTINDO O ENVIO DO CPF LIMPO PARA O PHP (essencial para a busca sem máscara)
            const buscaCpfClean = formData.get('busca_cpf').replace(/\D/g, '');
            formData.set('busca_cpf', buscaCpfClean); 

            // Desabilita o botão e mostra status
            btnBuscar.disabled = true;
            btnBuscar.innerHTML = '<i class="fas fa-spinner fa-spin me-1"></i> Buscando...';
            resultadoArea.innerHTML = '<div class="alert alert-warning">Carregando resultados...</div>'; 

            // ALTERAÇÃO AQUI: Chamando clientes_buscar_rapido.php
            fetch('clientes_buscar_rapido.php', { 
                method: 'POST',
                body: formData
            })
            .then(response => {
                if (!response.ok) {
                    throw new Error('Erro de rede: ' + response.statusText);
                }
                return response.text(); 
            }) 
            .then(html => {
                resultadoArea.innerHTML = html; 
            })
            .catch(error => {
                console.error('Erro na busca AJAX:', error);
                resultadoArea.innerHTML = '<div class="alert alert-danger">Erro ao buscar clientes. Por favor, verifique o console para mais detalhes.</div>';
            })
            .finally(() => {
                btnBuscar.disabled = false;
                btnBuscar.innerHTML = '<i class="fas fa-search me-1"></i> Buscar Clientes';
            });
        });
        
        // 3. Lógica do botão "Listar Todos"
        const btnListarTodos = document.getElementById('btn-listar-todos-clientes');
        if (btnListarTodos) {
            btnListarTodos.addEventListener('click', function() {
                // Limpa todos os campos antes de submeter
                document.getElementById('busca_id').value = '';
                document.getElementById('busca_cpf').value = '';
                document.getElementById('busca_nome').value = '';
                
                // Dispara a submissão do formulário, que agora irá listar todos
                formBusca.dispatchEvent(new Event('submit'));
            });
        }
        
        // 4. Lógica do botão "Esconder"
        const btnEsconder = document.getElementById('btn-esconder-clientes');
        if (btnEsconder) {
            btnEsconder.addEventListener('click', function() {
                resultadoArea.innerHTML = '<div class="alert alert-info">Resultados escondidos. Clique em "Buscar Clientes" ou "Listar Todos" para reexibir.</div>';
            });
        }
    });
</script>