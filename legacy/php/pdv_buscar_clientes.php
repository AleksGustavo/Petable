<?php
require_once 'conexao.php';

header('Content-Type: application/json');

$resultados = [];

if (isset($_GET['term']) && !empty($_GET['term'])) {
    $termo = '%' . $_GET['term'] . '%';

    try {
        $sql = "SELECT id, nome, cpf FROM cliente 
                WHERE ativo = 1 
                AND (nome LIKE :termo OR cpf LIKE :termo_limpo OR id = :id_busca)
                ORDER BY nome ASC 
                LIMIT 10";

        $termo_limpo = preg_replace('/[^0-9]/', '', $_GET['term']); 

        $stmt = $pdo->prepare($sql);
        $stmt->bindValue(':termo', $termo, PDO::PARAM_STR);
        $stmt->bindValue(':termo_limpo', '%' . $termo_limpo . '%', PDO::PARAM_STR);
        
        $id_busca = is_numeric($_GET['term']) ? (int)$_GET['term'] : 0;
        $stmt->bindValue(':id_busca', $id_busca, PDO::PARAM_INT);
        
        $stmt->execute();
        $clientes = $stmt->fetchAll(PDO::FETCH_ASSOC);

        foreach ($clientes as $cliente) {
            
            $label = $cliente['id'] . ' - ' . htmlspecialchars($cliente['nome']);
            if (!empty($cliente['cpf'])) {
                 $label .= ' (CPF: ' . $cliente['cpf'] . ')';
            }
            
            $resultados[] = [
                'label' => $label,
                'value' => htmlspecialchars($cliente['nome']), 
                'id'    => $cliente['id'], 
                'nome'  => $cliente['nome'] 
            ];
        }

    } catch (\PDOException $e) {
        error_log("Erro na busca de clientes (AJAX): " . $e->getMessage());
        $resultados[] = [
            'label' => 'Erro ao buscar clientes.',
            'value' => ''
        ];
    }
}

echo json_encode($resultados);
?>