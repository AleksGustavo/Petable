<?php
require_once 'conexao.php';

if (!isset($_GET['q']) || trim($_GET['q']) === '') {
    echo json_encode([]);
    exit;
}

$busca = mysqli_real_escape_string($conexao, $_GET['q']);
$sql = "
    SELECT id, nome, doenca_protecao, validade_padrao_meses 
    FROM vacina 
    WHERE ativo = 1 AND nome LIKE '{$busca}%' 
    ORDER BY nome ASC
";

$result = mysqli_query($conexao, $sql);
$vacinas = [];

if ($result) {
    while ($v = mysqli_fetch_assoc($result)) {
        $vacinas[] = [
            'id' => $v['id'],
            'nome' => $v['nome'],
            'validade_meses' => (int)$v['validade_padrao_meses'],
            'doenca_protecao' => $v['doenca_protecao']
        ];
    }
}

echo json_encode($vacinas);
mysqli_close($conexao);
?>
