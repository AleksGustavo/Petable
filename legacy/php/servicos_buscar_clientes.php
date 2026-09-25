<?php
require_once 'conexao.php';

if (!isset($_GET['q']) || trim($_GET['q']) === '') {
    echo json_encode([]);
    exit;
}

$busca = mysqli_real_escape_string($conexao, $_GET['q']);
$sql = "
    SELECT id, nome 
    FROM cliente 
    WHERE ativo = 1 AND nome LIKE '{$busca}%' 
    ORDER BY nome ASC
";

$result = mysqli_query($conexao, $sql);
$clientes = [];

if ($result) {
    while ($c = mysqli_fetch_assoc($result)) {
        $clientes[] = ['id' => $c['id'], 'nome' => $c['nome']];
    }
}

echo json_encode($clientes);
mysqli_close($conexao);
?>
