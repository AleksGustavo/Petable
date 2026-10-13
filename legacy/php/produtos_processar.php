<?php
ob_start(); 
require_once 'conexao.php';

header('Content-Type: application/json');
$response = ['success' => false, 'message' => 'Erro desconhecido.'];

if (!isset($conexao) || $conexao->connect_error) { 
    $response['message'] = 'Erro de conexão com o banco de dados. Verifique o arquivo conexao.php.';
    ob_clean();
    echo json_encode($response);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    $nome = trim($_POST['nome'] ?? '');
    $codigo_barras = trim($_POST['codigo_barras'] ?? '');
    $descricao = trim($_POST['descricao'] ?? '');
    
    $preco_custo = str_replace(',', '.', $_POST['preco_custo'] ?? 0.00); 
    $preco_venda = str_replace(',', '.', $_POST['preco_venda'] ?? 0.00);
    $quantidade_estoque = (int)($_POST['quantidade_estoque'] ?? 0);
    $estoque_minimo = (int)($_POST['estoque_minimo'] ?? 5);
    
    $categoria_id = (int)($_POST['categoria_id'] ?? 0);
    
    $fornecedor_padrao_id = (int)($_POST['fornecedor_padrao_id'] ?? 0);
    $fornecedor_padrao_id = $fornecedor_padrao_id > 0 ? $fornecedor_padrao_id : null; 

    if (empty($nome) || $categoria_id <= 0 || $preco_venda <= 0) {
        $response['message'] = 'Nome do produto, Categoria ou Preço de Venda são obrigatórios.';
    } else {
        try {
            $sql = "INSERT INTO produto (
                        nome, codigo_barras, descricao, preco_custo, preco_venda, 
                        quantidade_estoque, estoque_minimo, categoria_id, fornecedor_padrao_id
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?
                    )";
            
            $stmt = $conexao->prepare($sql);

            $tipos = "ssddiiiii"; 
            $fornecedor_bind = $fornecedor_padrao_id ?? 0;

            $stmt->bind_param($tipos, 
                $nome, 
                $codigo_barras, 
                $descricao, 
                $preco_custo, 
                $preco_venda, 
                $quantidade_estoque, 
                $estoque_minimo, 
                $categoria_id, 
                $fornecedor_bind
            );
            
            if ($stmt->execute()) {
                $response['success'] = true;
                $response['message'] = 'Produto cadastrado com sucesso!';
                $response['id_inserido'] = $conexao->insert_id; 
            } else {
                $response['message'] = 'Falha ao executar a inserção: ' . $stmt->error;
            }

            $stmt->close();

        } catch (Exception $e) {
            $response['message'] = 'Erro ao salvar o produto: ' . $e->getMessage();
        }
    }
} else {
    $response['message'] = 'Método de requisição inválido.';
}

ob_clean();
echo json_encode($response);
exit;
?>
