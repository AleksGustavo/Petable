<?php
// Arquivo: servicos_agendamento_processar.php
// Lógica de processamento de ações (concluir, cancelar, deletar) para agendamentos via AJAX.

// Define o tipo de conteúdo como JSON
header('Content-Type: application/json');

require_once 'conexao.php'; // Inclua o arquivo de conexão MySQLi

// Variável de resposta padrão
$response = [
    'sucesso' => false,
    'mensagem' => '<div class="alert alert-danger">Erro desconhecido na requisição.</div>'
];

// Verifica se a requisição é POST
if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    
    // =========================================================================
    // 1. COLETAR E VALIDAR PARÂMETROS
    // =========================================================================
    
    // O ID do agendamento vem via POST
    $agendamento_id = filter_input(INPUT_POST, 'id', FILTER_VALIDATE_INT);
    
    // A AÇÃO vem na URL (GET) e precisa ser lida daqui.
    $acao = filter_input(INPUT_GET, 'acao', FILTER_SANITIZE_STRING); // <-- CORREÇÃO CRÍTICA AQUI!

    if (!$agendamento_id || !is_numeric($agendamento_id) || empty($acao)) {
        $response['mensagem'] = '<div class="alert alert-danger">Parâmetros inválidos. ID do agendamento ou Ação não fornecida.</div>';
        echo json_encode($response);
        exit;
    }

    // Garante que a conexão mysqli está disponível
    if (!isset($conexao) || !$conexao) {
        $response['mensagem'] = '<div class="alert alert-danger">Erro crítico: Conexão com o banco de dados indisponível.</div>';
        echo json_encode($response);
        exit;
    }

    // =========================================================================
    // 2. EXECUTAR AÇÃO COM BASE NO PARÂMETRO 'ACAO'
    // =========================================================================
    try {
        $stmt = null;
        $sucesso = false;
        $mensagem_sucesso = '';

        switch ($acao) {
            case 'concluir_status':
                $novo_status = 'concluido';
                $sql = "UPDATE agendamento SET status = ? WHERE id = ?";
                $stmt = mysqli_prepare($conexao, $sql);
                mysqli_stmt_bind_param($stmt, 'si', $novo_status, $agendamento_id);
                $mensagem_sucesso = "Agendamento **#$agendamento_id** marcado como Concluído com sucesso.";
                break;

            case 'cancelar_status':
                $novo_status = 'cancelado';
                $sql = "UPDATE agendamento SET status = ? WHERE id = ?";
                $stmt = mysqli_prepare($conexao, $sql);
                mysqli_stmt_bind_param($stmt, 'si', $novo_status, $agendamento_id);
                $mensagem_sucesso = "Agendamento **#$agendamento_id** cancelado com sucesso.";
                break;

            case 'deletar':
                $sql = "DELETE FROM agendamento WHERE id = ?";
                $stmt = mysqli_prepare($conexao, $sql);
                mysqli_stmt_bind_param($stmt, 'i', $agendamento_id);
                $mensagem_sucesso = "Agendamento **#$agendamento_id** excluído permanentemente com sucesso.";
                break;

            default:
                $response['mensagem'] = '<div class="alert alert-warning">Ação solicitada desconhecida.</div>';
                echo json_encode($response);
                exit;
        }

        // Executa o comando SQL
        if ($stmt && mysqli_stmt_execute($stmt)) {
            if (mysqli_stmt_affected_rows($stmt) > 0) {
                $response['sucesso'] = true;
                // Retorna a mensagem formatada para ser exibida no status-message-area do frontend
                $alert_class = ($acao === 'deletar') ? 'alert-danger' : 'alert-success';
                $response['mensagem'] = "<div class='alert $alert_class'>$mensagem_sucesso</div>";
            } else {
                $response['mensagem'] = '<div class="alert alert-warning">Nenhuma linha afetada. O agendamento não existe ou já está com o status desejado.</div>';
            }
        } else if ($stmt) {
             // Erro de execução
             $response['mensagem'] = '<div class="alert alert-danger">Erro ao executar a operação: ' . mysqli_error($conexao) . '</div>';
        }
        
        // Fecha o statement
        if ($stmt) {
             mysqli_stmt_close($stmt);
        }

    } catch (\Exception $e) {
        // Erro genérico
        $response['mensagem'] = '<div class="alert alert-danger">Erro interno do servidor: ' . $e->getMessage() . '</div>';
    }
} else {
    $response['mensagem'] = '<div class="alert alert-danger">Acesso negado. Apenas requisições POST são permitidas.</div>';
}

// =========================================================================
// 3. RETORNAR RESPOSTA JSON
// =========================================================================
echo json_encode($response);
?>