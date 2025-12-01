<?php

ob_start(); 
require_once 'conexao.php';
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
            $pdo->beginTransaction();

            $stmt_venda = $pdo->prepare("DELETE FROM item_venda WHERE produto_id = :id");
            $stmt_venda->execute([':id' => $id_produto]);

            $stmt_compra = $pdo->prepare("DELETE FROM item_compra WHERE produto_id = :id");
            $stmt_compra->execute([':id' => $id_produto]);

            $stmt_produto = $pdo->prepare("DELETE FROM produto WHERE id = :id");
            $stmt_produto->bindParam(':id', $id_produto, PDO::PARAM_INT);
            
            if ($stmt_produto->execute()) {
                
                if ($stmt_produto->rowCount() > 0) {
                    $pdo->commit();
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
