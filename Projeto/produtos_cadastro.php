<?php
// Arquivo: produtos_cadastro.php
// Este arquivo carrega o HTML do formulário de cadastro de produtos.

// Inclui o script auxiliar para buscar categorias e fornecedores
require_once 'produtos_dados.php';

// Obtém as listas para os campos <select>
$categorias = get_categorias($pdo);
$fornecedores = get_fornecedores($pdo);
?>

<div class="d-flex justify-content-between align-items-center mb-3">
    <h2><i class="fas fa-box me-2"></i> Cadastrar Novo Produto</h2>
    
    <div>
        <a href="#" class="btn btn-secondary item-menu-ajax btn-sm" data-pagina="produtos_listar.php">
            <i class="fas fa-list me-2"></i> Voltar à Lista
        </a>
    </div>
</div>

<div id="status-message-area">
</div>

<div class="card p-0 shadow-sm main-compact-card">
    <div class="card-body">
        <form id="form-cadastro-produto" method="POST" action="produtos_processar.php">
            <div class="row g-2 g-compact">
                
                <div class="col-md-8">
                    <label for="nome" class="form-label">Nome do Produto *</label>
                    <input type="text" id="nome" name="nome" class="form-control form-control-sm" required>
                </div>
                
                <div class="col-md-4">
                    <label for="codigo_barras" class="form-label">Código de Barras</label>
                    <input type="text" id="codigo_barras" name="codigo_barras" class="form-control form-control-sm">
                </div>
                
                <div class="col-md-12">
                    <label for="descricao" class="form-label">Descrição</label>
                    <textarea id="descricao" name="descricao" class="form-control form-control-sm" rows="1"></textarea>
                </div>

                <hr class="mt-3 mb-2">
                <h5 class="mb-2">Dados de Custo e Estoque</h5>

                <div class="col-md-3">
                    <label for="preco_custo" class="form-label">Preço de Custo</label>
                    <input type="number" step="0.01" min="0" id="preco_custo" name="preco_custo" class="form-control form-control-sm">
                </div>
                
                <div class="col-md-3">
                    <label for="preco_venda" class="form-label">Preço de Venda *</label>
                    <input type="number" step="0.01" min="0" id="preco_venda" name="preco_venda" class="form-control form-control-sm" required>
                </div>

                <div class="col-md-3">
                    <label for="quantidade_estoque" class="form-label">Estoque Inicial</label>
                    <input type="number" min="0" id="quantidade_estoque" name="quantidade_estoque" class="form-control form-control-sm" value="0">
                </div>

                <div class="col-md-3">
                    <label for="estoque_minimo" class="form-label">Estoque Mínimo</label>
                    <input type="number" min="0" id="estoque_minimo" name="estoque_minimo" class="form-control form-control-sm" value="5">
                </div>

                <hr class="mt-3 mb-2">
                <h5 class="mb-2">Configurações</h5>

                <div class="col-md-6">
                    <label for="categoria_id" class="form-label">Categoria *</label>
                    <select id="categoria_id" name="categoria_id" class="form-select form-select-sm" required>
                        <option value="">Selecione a Categoria...</option>
                        <?php foreach ($categorias as $cat): ?>
                            <option value="<?= htmlspecialchars($cat['id']) ?>">
                                <?= htmlspecialchars($cat['nome']) ?>
                            </option>
                        <?php endforeach; ?>
                    </select>
                </div>

                <div class="col-md-6">
                    <label for="fornecedor_padrao_id" class="form-label">Fornecedor Padrão (Opcional)</label>
                    <select id="fornecedor_padrao_id" name="fornecedor_padrao_id" class="form-select form-select-sm">
                        <option value="">Nenhum / Selecione o Fornecedor...</option>
                        <?php foreach ($fornecedores as $forn): ?>
                            <option value="<?= htmlspecialchars($forn['id']) ?>">
                                <?= htmlspecialchars($forn['nome_fantasia']) ?>
                            </option>
                        <?php endforeach; ?>
                    </select>
                </div>

                <div class="col-12 mt-3">
                    <button type="submit" class="btn btn-success btn-sm">
                        <i class="fas fa-save me-2"></i> Salvar Produto
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>