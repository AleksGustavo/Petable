<?php
// Arquivo: produtos_processar.php
// Responsável por inserir os dados do novo produto no banco de dados, usando MySQLi.

ob_start(); 
// Adapte o caminho abaixo para o seu arquivo de conexão MySQLi
require_once 'conexao.php'; 

// 1. Configuração de resposta JSON
header('Content-Type: application/json');
$response = ['success' => false, 'message' => 'Erro desconhecido.'];

// Verifica se a variável de conexão existe e é válida
if (!isset($conn) || $conn->connect_error) {
    $response['message'] = 'Erro de conexão com o banco de dados. Verifique o arquivo conexao.php.';
    ob_clean();
    echo json_encode($response);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    // 2. Coleta e Preparação dos Dados
    // Use mysqli_real_escape_string para sanitizar strings antes da inserção, 
    // embora PREPARED STATEMENTS (que usaremos) sejam muito mais seguros.
    
    $nome = trim($_POST['nome'] ?? '');
    $codigo_barras = trim($_POST['codigo_barras'] ?? '');
    $descricao = trim($_POST['descricao'] ?? '');
    
    // Converta para ponto decimal (necessário para a maioria dos bancos)
    $preco_custo = str_replace(',', '.', $_POST['preco_custo'] ?? 0.00); 
    $preco_venda = str_replace(',', '.', $_POST['preco_venda'] ?? 0.00);
    $quantidade_estoque = (int)($_POST['quantidade_estoque'] ?? 0);
    $estoque_minimo = (int)($_POST['estoque_minimo'] ?? 5);
    
    $categoria_id = (int)($_POST['categoria_id'] ?? 0);
    // Se o fornecedor for nulo, usamos null, senão o ID.
    $fornecedor_padrao_id = (int)($_POST['fornecedor_padrao_id'] ?? 0);
    $fornecedor_padrao_id = $fornecedor_padrao_id > 0 ? $fornecedor_padrao_id : null; 

    // 3. Validação básica
    if (empty($nome) || $categoria_id <= 0 || $preco_venda <= 0) {
        $response['message'] = 'Nome do produto, Categoria ou Preço de Venda são obrigatórios.';
    } else {
        try {
            // CORRIGIDO: O nome da tabela é 'produto' (singular)
            $sql = "INSERT INTO produto (
                        nome, codigo_barras, descricao, preco_custo, preco_venda, 
                        quantidade_estoque, estoque_minimo, categoria_id, fornecedor_padrao_id
                    ) VALUES (
                        ?, ?, ?, ?, ?, ?, ?, ?, ?
                    )";
            
            // Usando Prepared Statements (MUITO MAIS SEGURO)
            $stmt = $conn->prepare($sql);
            
            // O tipo de dado deve corresponder: 
            // s = string, d = double/float, i = integer
            $stmt->bind_param("sssdiiii", 
                $nome, 
                $codigo_barras, 
                $descricao, 
                $preco_custo, 
                $preco_venda, 
                $quantidade_estoque, 
                $estoque_minimo, 
                $categoria_id, 
                $fornecedor_padrao_id
            );
            
            if ($stmt->execute()) {
                // Sucesso na inserção
                $response['success'] = true;
                $response['message'] = 'Produto cadastrado com sucesso!';
                $response['id_inserido'] = $conn->insert_id; 
            } else {
                // Se a execução falhar (ex: erro de chave estrangeira)
                $response['message'] = 'Falha ao executar a inserção: ' . $stmt->error;
            }

            $stmt->close();

        } catch (Exception $e) {
            // Em caso de exceção (ex: nome de tabela errado)
            $response['message'] = 'Erro ao salvar o produto: ' . $e->getMessage();
        }
    }
} else {
    $response['message'] = 'Método de requisição inválido.';
}

ob_clean(); // Limpa o buffer de saída antes de enviar o JSON
echo json_encode($response);
exit;
?>