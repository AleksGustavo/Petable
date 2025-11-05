<?php
// Arquivo: produtos_excluir.php
// Objetivo: Excluir permanentemente o produto e suas dependências (Vendas/Compras).

// 1. Obriga o uso de buffer de saída
ob_start(); 
require_once 'conexao.php'; // Sua conexão PDO
header('Content-Type: application/json');

$response = [
    'success' => false,
    'message' => 'Requisição inválida.'
];

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['id_produto'])) {
    
    $id_produto = (int)$_POST['id_produto'];

    if ($id_produto <= 0) {
        $response['message'] = 'ID do produto inválido.';
    } else {
        try {
            // INICIA TRANSAÇÃO: Essencial para exclusão forçada
            $pdo->beginTransaction();

            // ====================================================================
            // 1. EXCLUIR REGISTROS DEPENDENTES (Baseado no seu último dump SQL)
            // As tabelas dependentes estão em singular: 'item_venda' e 'item_compra'
            // ====================================================================
            
            // 1.1. Deletar itens de VENDA que referenciam este produto
            $stmt_venda = $pdo->prepare("DELETE FROM item_venda WHERE produto_id = :id");
            $stmt_venda->execute([':id' => $id_produto]);
            
            // 1.2. Deletar itens de COMPRA que referenciam este produto
            $stmt_compra = $pdo->prepare("DELETE FROM item_compra WHERE produto_id = :id");
            $stmt_compra->execute([':id' => $id_produto]);
            
            // =========================================================
            // 2. EXCLUIR O PRODUTO PRINCIPAL (Tabela 'produto' singular)
            // =========================================================
            $stmt_produto = $pdo->prepare("DELETE FROM produto WHERE id = :id");
            $stmt_produto->bindParam(':id', $id_produto, PDO::PARAM_INT);
            
            if ($stmt_produto->execute()) {
                
                if ($stmt_produto->rowCount() > 0) {
                    $pdo->commit(); // Confirma todas as exclusões
                    $response['success'] = true;
                    $response['message'] = "Produto ID $id_produto e todos os seus registros de venda/compra foram excluídos permanentemente.";
                } else {
                    $pdo->rollBack(); 
                    $response['message'] = "Erro: Produto ID $id_produto não encontrado para exclusão.";
                }
            } else {
                $pdo->rollBack(); 
                $response['message'] = 'Erro ao executar a exclusão final do produto.';
            }
            
        } catch (PDOException $e) {
            $pdo->rollBack(); 
            $response['message'] = 'Erro fatal no banco de dados. Verifique outras possíveis dependências: ' . $e->getMessage();
        }
    }
}

ob_clean();
echo json_encode($response);
exit; 
?>