<?php
// Arquivo: clientes_cadastro.php
// Apenas o conteúdo HTML que será injetado pelo AJAX.
?>

<div class="d-flex justify-content-between align-items-center mb-3">
    <h2><i class="fas fa-user-plus me-2"></i> Cadastrar Novo Cliente</h2>
    
    <div>
        <a href="#" class="btn btn-secondary item-menu-ajax btn-sm" data-pagina="clientes_listar.php">
            <i class="fas fa-list me-2"></i> Voltar à Lista
        </a>
    </div>
</div>

<div id="status-message-area">
</div>

<div class="card p-0 shadow-sm main-compact-card">
    <div class="card-body">
        <form id="form-cadastro-cliente" method="POST" action="clientes_processar.php">
            <div class="row g-2 g-compact">
                
                <div class="col-md-4">
                    <label for="nome" class="form-label">Nome *</label>
                    <input type="text" id="nome" name="nome" class="form-control form-control-sm input-letters-only" required>
                </div>
                
                <div class="col-md-4">
                    <label for="sobrenome" class="form-label">Sobrenome *</label>
                    <input type="text" id="sobrenome" name="sobrenome" class="form-control form-control-sm input-letters-only" required>
                </div>
                
                <div class="col-md-4">
                    <label for="cpf" class="form-label">CPF *</label>
                    <input type="text" id="cpf" name="cpf" class="form-control form-control-sm mask-cpf input-numbers-only" required maxlength="14" placeholder="000.000.000-00">
                </div>
                
                <div class="col-md-4">
                    <label for="celular" class="form-label">Celular *</label>
                    <input type="text" id="celular" name="celular" class="form-control form-control-sm mask-celular input-numbers-only" required maxlength="15" placeholder="(00) 00000-0000">
                </div>

                <hr class="mt-2">
                <h5 class="mb-2">Endereço</h5>

                <div class="col-md-3">
                    <label for="cep" class="form-label">CEP *</label>
                    <input type="text" id="cep" name="cep" class="form-control form-control-sm mask-cep input-numbers-only" required maxlength="9" placeholder="00000-000">
                </div>
                
                <div class="col-md-6">
                    <label for="rua" class="form-label">Rua *</label>
                    <input type="text" id="rua" name="rua" class="form-control form-control-sm" required>
                </div>
                
                <div class="col-md-3">
                    <label for="numero" class="form-label">Número *</label>
                    <input type="text" id="numero" name="numero" class="form-control form-control-sm input-numbers-only" required>
                </div>
                
                <div class="col-md-6">
                    <label for="bairro" class="form-label">Bairro</label>
                    <input type="text" id="bairro" name="bairro" class="form-control form-control-sm input-letters-only">
                </div>
                
                <div class="col-md-6">
                    <label for="complemento" class="form-label">Complemento</label>
                    <input type="text" id="complemento" name="complemento" class="form-control form-control-sm">
                </div>

                <hr class="mt-2">
                <h5 class="mb-2">Outros Dados</h5>

                <div class="col-md-4">
                    <label for="data_nascimento" class="form-label">Data de Nascimento</label>
                    <input type="date" id="data_nascimento" name="data_nascimento" class="form-control form-control-sm">
                </div>
                <div class="col-md-4">
                    <label for="sexo" class="form-label">Sexo</label>
                    <select id="sexo" name="sexo" class="form-select form-select-sm">
                        <option value="">Selecione...</option>
                        <option value="M">Masculino</option>
                        <option value="F">Feminino</option>
                        <option value="Outro">Outro</option>
                    </select>
                </div>
                
                <div class="col-12 mt-3">
                    <button type="submit" class="btn btn-success btn-sm">
                        <i class="fas fa-save me-2"></i> Cadastrar Cliente
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>