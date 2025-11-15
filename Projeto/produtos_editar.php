<?php
// Arquivo: produtos_editar.php
// Carrega os dados de um produto para edição e processa a atualização

// ATIVAR MÁXIMO DE ERROS PARA DEBUG (MUITO IMPORTANTE!)
error_reporting(E_ALL);
ini_set('display_errors', 1);

require_once 'conexao.php'; // REQUISITO: Este arquivo deve existir e definir a variável $pdo (PDO)

// Variáveis de estado
$produto = null;
$mensagem_status = '';
$tipo_status = '';

// ------------------------------------------
// 1. Receber ID e Tentar Carregar Produto
// ------------------------------------------
$produto_id = filter_input(INPUT_GET, 'id', FILTER_SANITIZE_NUMBER_INT);

// Verifica se há um status na URL após um redirecionamento de sucesso
$status_url = filter_input(INPUT_GET, 'status', FILTER_SANITIZE_SPECIAL_CHARS);
if ($status_url === 'success') {
    $mensagem_status = 'Produto atualizado com sucesso!';
    $tipo_status = 'success';
}

if (!$produto_id || $produto_id <= 0) {
    $mensagem_status = 'ID do produto inválido ou não fornecido.';
    $tipo_status = 'danger';
} else {
    // ------------------------------------------
    // 2. Processar Submissão do Formulário (POST)
    // ------------------------------------------
    if ($_SERVER['REQUEST_METHOD'] === 'POST') {
        try {
            $nome = trim(filter_input(INPUT_POST, 'nome', FILTER_SANITIZE_SPECIAL_CHARS));
            
            // Tratamento de preço: converte vírgula (,) para ponto (.)
            $preco_input = filter_input(INPUT_POST, 'preco_venda', FILTER_SANITIZE_STRING);
            $preco_venda = (float)str_replace(',', '.', $preco_input);
            
            $quantidade_estoque = (int)filter_input(INPUT_POST, 'quantidade_estoque', FILTER_SANITIZE_NUMBER_INT);
            $ativo = filter_input(INPUT_POST, 'ativo') === 'on' ? 1 : 0;
            
            // =================================================================
            // 🚨 DEBUG CRÍTICO: VERIFICAÇÃO DOS DADOS ANTES DE SALVAR
            // =================================================================
            // Remova este bloco após o código funcionar!
            /*
            echo "<pre><h3>DEBUG: Dados Recebidos</h3>";
            echo "ID: " . $produto_id . "\n";
            echo "Nome: " . $nome . "\n";
            echo "Preço Venda (float): " . $preco_venda . "\n";
            echo "Estoque (int): " . $quantidade_estoque . "\n";
            echo "Ativo (0/1): " . $ativo . "\n";
            echo "</pre><hr>";
            // */
            // =================================================================
            
            if (empty($nome) || $preco_venda < 0 || $quantidade_estoque < 0) {
                $mensagem_status = 'Por favor, preencha todos os campos obrigatórios corretamente.';
                $tipo_status = 'warning';
            } else {
                $sql_update = "UPDATE produto SET 
                                nome = :nome, 
                                preco_venda = :preco_venda, 
                                quantidade_estoque = :quantidade_estoque, 
                                ativo = :ativo 
                                WHERE id = :id";
                
                $stmt_update = $pdo->prepare($sql_update);
                $execucao = $stmt_update->execute([
                    ':nome' => $nome,
                    ':preco_venda' => $preco_venda,
                    ':quantidade_estoque' => $quantidade_estoque,
                    ':ativo' => $ativo,
                    ':id' => $produto_id
                ]);

                // 🚨 Se a execução for falsa (execução do PDO falhou)
                if ($execucao === false) {
                     $mensagem_status = 'Erro grave! A execução da consulta falhou. Verifique os logs do servidor.';
                     $tipo_status = 'danger';
                     // Debug extra: se a execução falhou, tente obter o erro
                     error_log("PDO Error: " . print_r($stmt_update->errorInfo(), true));
                }
                
                // Redireciona APENAS se alguma linha foi afetada
                if ($stmt_update->rowCount() > 0) {
                    // Implementação do PRG: Redireciona para a própria página com a flag 'status=success'
                    header('Location: produtos_editar.php?id=' . $produto_id . '&status=success');
                    exit; // CRÍTICO: Interrompe a execução!
                } else {
                    $mensagem_status = 'Nenhuma alteração foi realizada (os dados enviados são idênticos aos atuais).';
                    $tipo_status = 'info';
                }
            }
        } catch (\PDOException $e) {
            $mensagem_status = 'Erro fatal ao atualizar o produto no banco de dados. Detalhe: ' . $e->getMessage();
            $tipo_status = 'danger';
            error_log("Erro de atualização de produto (PDO): " . $e->getMessage());
        } catch (\Exception $e) {
            $mensagem_status = 'Erro inesperado: ' . $e->getMessage();
            $tipo_status = 'danger';
        }
    }
    
    // ------------------------------------------
    // 3. Carregar dados atuais do produto (GET ou pós-POST)
    // ------------------------------------------
    // Esta parte é crucial para recarregar o produto com os dados atualizados
    try {
        $sql_select = "SELECT id, nome, preco_venda, quantidade_estoque, ativo 
                       FROM produto 
                       WHERE id = :id";
        $stmt_select = $pdo->prepare($sql_select);
        $stmt_select->execute([':id' => $produto_id]);
        $produto = $stmt_select->fetch(PDO::FETCH_ASSOC);

        if (!$produto) {
            $mensagem_status = 'Produto não encontrado no sistema.';
            $tipo_status = 'danger';
        }

    } catch (\PDOException $e) {
        $mensagem_status = 'Erro ao buscar dados do produto. Detalhe: ' . $e->getMessage();
        $tipo_status = 'danger';
        error_log("Erro de busca de produto: " . $e->getMessage());
    }
}
?>

<!DOCTYPE html>
<html lang="pt-BR">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Editar Produto #<?php echo $produto_id; ?></title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet">
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.2/css/all.min.css">
</head>
<body>
    <div class="container mt-5">
        <div class="row justify-content-center">
            <div class="col-md-8">
                
                <h2 class="mb-4">
                    <i class="fas fa-edit me-2"></i> Editar Produto 
                    <?php echo $produto ? "#{$produto['id']} - " . htmlspecialchars($produto['nome']) : 'ID ' . $produto_id; ?>
                </h2>

                <?php if ($mensagem_status): ?>
                    <div class="alert alert-<?php echo $tipo_status; ?> alert-dismissible fade show" role="alert">
                        <?php echo $mensagem_status; ?>
                        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
                    </div>
                <?php endif; ?>

                <?php if ($produto): ?>
                <div class="card shadow-sm">
                    <div class="card-body">
                        <form method="POST">
                            
                            <div class="mb-3">
                                <label for="nome" class="form-label">Nome do Produto</label>
                                <input type="text" class="form-control" id="nome" name="nome" 
                                       value="<?php echo htmlspecialchars($produto['nome'] ?? ''); ?>" required>
                            </div>
                            
                            <div class="row">
                                <div class="col-md-6 mb-3">
                                    <label for="preco_venda" class="form-label">Preço de Venda (R$)</label>
                                    <input type="text" class="form-control" id="preco_venda" name="preco_venda" 
                                           value="<?php echo number_format($produto['preco_venda'] ?? 0.00, 2, ',', '.'); ?>" required 
                                           pattern="[0-9]+([,\.][0-9]{2})?">
                                    <small class="form-text text-muted">Use **vírgula** como separador decimal (ex: 12,50).</small>
                                </div>

                                <div class="col-md-6 mb-3">
                                    <label for="quantidade_estoque" class="form-label">Quantidade em Estoque</label>
                                    <input type="number" class="form-control" id="quantidade_estoque" name="quantidade_estoque" 
                                           value="<?php echo htmlspecialchars($produto['quantidade_estoque'] ?? 0); ?>" required min="0">
                                </div>
                            </div>

                            <div class="mb-3 form-check">
                                <input type="checkbox" class="form-check-input" id="ativo" name="ativo" 
                                       <?php echo ($produto['ativo'] ?? 0) == 1 ? 'checked' : ''; ?>>
                                <label class="form-check-label" for="ativo">Produto Ativo para Venda</label>
                            </div>
                            
                            <hr>

                            <button type="submit" class="btn btn-primary w-100">
                                <i class="fas fa-save me-2"></i> Salvar Alterações
                            </button>
                            
                            <a href="produtos_listar.php" class="btn btn-secondary w-100 mt-2">
                                <i class="fas fa-arrow-left me-2"></i> Voltar para a lista
                            </a>
                        </form>
                    </div>
                </div>
                <?php endif; ?>

            </div>
        </div>
    </div>
    
    <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
</body>
</html>