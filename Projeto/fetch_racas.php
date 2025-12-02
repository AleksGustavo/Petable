<?php
// Inclui o arquivo de conexão PDO
require_once 'conexao.php'; 

// Define o cabeçalho para retornar dados em formato JSON
header('Content-Type: application/json');

// Pega o ID da espécie enviado via GET
$especie_id = $_GET['especie_id'] ?? null;
$racas = [];

if ($especie_id) {
    try {
        // Validação e sanitização básica do ID
        $especie_id = filter_var($especie_id, FILTER_VALIDATE_INT);
        
        if ($especie_id) {
            // Consulta preparada para buscar APENAS as raças da espécie_id fornecida
            // A tabela 'raca' tem a coluna 'especie_id' para fazer o filtro.
            $stmt = $pdo->prepare("SELECT id, nome FROM raca WHERE especie_id = ? ORDER BY nome ASC");
            $stmt->execute([$especie_id]);
            $racas = $stmt->fetchAll(PDO::FETCH_ASSOC);
        }

    } catch (PDOException $e) {
        // Log de erro (para debug) e retorna array vazio
        error_log("Erro ao buscar raças por espécie: " . $e->getMessage());
    }
}

// Retorna o array de raças como JSON
echo json_encode($racas);
?>