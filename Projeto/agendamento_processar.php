<?php
// Arquivo: agendamento_processar.php

require_once 'conexao.php'; 
header('Content-Type: application/json');

$response = ['success' => false, 'message' => 'Ação inválida.'];

if ($_SERVER['REQUEST_METHOD'] === 'POST' && isset($_POST['acao']) && isset($_POST['id'])) {
    $acao = $_POST['acao'];
    $agendamento_id = filter_input(INPUT_POST, 'id', FILTER_VALIDATE_INT);

    if (!$agendamento_id) {
        $response['message'] = 'ID de Agendamento inválido.';
    } else {
        mysqli_begin_transaction($conexao);
        try {
            $sql = '';
            $params = [];
            $types = '';

            switch ($acao) {
                case 'concluir_status':
                case 'cancelar_status':
                    $novo_status = ($acao === 'concluir_status') ? 'concluido' : 'cancelado';
                    $sql = "UPDATE agendamento SET status = ? WHERE id = ?";
                    $types = 'si';
                    $params = [$novo_status, $agendamento_id];
                    $response['message'] = "Agendamento {$agendamento_id} marcado como " . ucfirst($novo_status) . " com sucesso.";
                    break;
                case 'deletar':
                    $sql = "DELETE FROM agendamento WHERE id = ?";
                    $types = 'i';
                    $params = [$agendamento_id];
                    $response['message'] = "Agendamento {$agendamento_id} excluído permanentemente.";
                    break;
                default:
                    throw new Exception('Ação não reconhecida.');
            }

            if (!empty($sql)) {
                $stmt = mysqli_prepare($conexao, $sql);
                if (!$stmt) {
                    throw new Exception("Erro na preparação da query: " . mysqli_error($conexao));
                }
                
                mysqli_stmt_bind_param($stmt, $types, ...$params);

                if (!mysqli_stmt_execute($stmt)) {
                    throw new Exception("Erro ao executar a ação no BDA: " . mysqli_stmt_error($stmt));
                }
                mysqli_stmt_close($stmt);
                mysqli_commit($conexao);
                $response['success'] = true;
            }

        } catch (Exception $e) {
            mysqli_rollback($conexao);
            $response['message'] = "Falha na ação. Detalhe: " . $e->getMessage();
        }
    }
}

echo json_encode($response);
if (isset($conexao)) {
    mysqli_close($conexao);
}
?>