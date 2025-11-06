<?php
// Arquivo: produtos_processar.php
// Responsável por inserir os dados do novo produto no banco de dados, usando MySQLi (Variável: $conexao).

ob_start(); 
require_once 'conexao.php'; // Inclui sua conexão dupla

// 1. Configuração de resposta JSON
header('Content-Type: application/json');
$response = ['success' => false, 'message' => 'Erro desconhecido.'];

// Verifica se a variável de conexão MySQLi ($conexao) existe e é válida
if (!isset($conexao) || $conexao->connect_error) { 
    // AGORA ESTÁ PROCURANDO A VARIÁVEL CORRETA ($conexao)
    $response['message'] = 'Erro de conexão com o banco de dados. Verifique o arquivo conexao.php.';
    ob_clean();
    echo json_encode($response);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    // 2. Coleta e Preparação dos Dados
    $nome = trim($_POST['nome'] ?? '');
    $codigo_barras = trim($_POST['codigo_barras'] ?? '');
    $descricao = trim($_POST['descricao'] ?? '');
    
    $preco_custo = str_replace(',', '.', $_POST['preco_custo'] ?? 0.00); 
    $preco_venda = str_replace(',', '.', $_POST['preco_venda'] ?? 0.00);
    $quantidade_estoque = (int)($_POST['quantidade_estoque'] ?? 0);
    $estoque_minimo = (int)($_POST['estoque_minimo'] ?? 5);
    
    $categoria_id = (int)($_POST['categoria_id'] ?? 0);
    
    // Trata o fornecedor_padrao_id para ser NULL se não for selecionado (0)
    $fornecedor_padrao_id = (int)($_POST['fornecedor_padrao_id'] ?? 0);
    $fornecedor_padrao_id = $fornecedor_padrao_id > 0 ? $fornecedor_padrao_id : null; 

    // 3. Validação básica
    if (empty($nome) || $categoria_id <= 0 || $preco_venda <= 0) {
        $response['message'] = 'Nome do produto, Categoria ou Preço de Venda são obrigatórios.';
    } else {
        try {
            // Tabela: 'produto' (singular)
            $sql = "INSERT INTO produto (
                        nome, codigo_barras, descricao, preco_custo, preco_venda, 
                        quantidade_estoque, estoque_minimo, categoria_id, fornecedor_padrao_id
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?
                    )";
            
            // Usando Prepared Statements no objeto $conexao
            $stmt = $conexao->prepare($sql); // AGORA USA $conexao
            
            // Tipos: s = string, d = double/float, i = integer, b = blob
            // Preço (d), Estoque (i), Categoria (i), Fornecedor (i ou null)
            $tipos = "sssdiiiis"; // Usamos 's' para o fornecedor_padrao_id porque ele pode ser NULL
            
            // O bind_param exige que as variáveis sejam passadas por referência, 
            // e $fornecedor_padrao_id precisa de tratamento especial se for NULL.
            // Para simplificar, vamos passar o fornecedor como inteiro ou 0 (e o DB faz o cast)
            $tipos = "ssddiiiii"; 
            $fornecedor_bind = $fornecedor_padrao_id ?? 0; // Se NULL, passa 0.

            $stmt->bind_param($tipos, 
                $nome, 
                $codigo_barras, 
                $descricao, 
                $preco_custo, 
                $preco_venda, 
                $quantidade_estoque, 
                $estoque_minimo, 
                $categoria_id, 
                $fornecedor_bind // Passado como inteiro/0
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