<?php
// Arquivo: clientes_listar.php
// ... (Seu código PHP) ...
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