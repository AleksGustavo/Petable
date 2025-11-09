<?php
// Arquivo: agendamento_processar.php
// Lida com as operações de CRUD (Criar, Editar, Deletar) para Agendamentos.

// Presume que conexao.php fornece a variável $pdo (PDO)
require_once 'conexao.php'; 

header('Content-Type: application/json');
$response = ['success' => false, 'message' => 'Erro desconhecido.'];

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $response['message'] = 'Método de requisição inválido.';
    echo json_encode($response);
    exit;
}

$acao = $_POST['acao'] ?? '';
$id = (int)($_POST['id'] ?? 0);

try {
    if ($acao === 'salvar') {
        // --- AÇÃO: SALVAR NOVO AGENDAMENTO ---
        
        $pet_id = (int)($_POST['pet_id'] ?? 0);
        $servico_id = (int)($_POST['servico_id'] ?? 0); 
        $data_agendamento = $_POST['data_agendamento'] ?? ''; // YYYY-MM-DD
        $hora_agendamento = $_POST['hora_agendamento'] ?? ''; // HH:MM
        $observacoes = $_POST['observacoes'] ?? '';
        
        if ($pet_id === 0 || $servico_id === 0 || empty($data_agendamento) || empty($hora_agendamento)) {
            $response['message'] = 'Dados obrigatórios (Pet, Serviço, Data/Hora) estão faltando.';
            echo json_encode($response);
            exit;
        }
        
        // Combina data e hora no formato DATETIME
        $data_hora = $data_agendamento . ' ' . $hora_agendamento . ':00';
        
        $sql = "INSERT INTO agendamento (pet_id, servico_id, data_agendamento, observacoes) 
                VALUES (?, ?, ?, ?)";
        
        $stmt = $pdo->prepare($sql);
        $stmt->execute([$pet_id, $servico_id, $data_hora, $observacoes]);
        
        $response['success'] = true;
        $response['message'] = "Agendamento criado com sucesso! Redirecionando...";

    } elseif ($acao === 'editar' && $id > 0) {
        // --- AÇÃO: EDITAR AGENDAMENTO EXISTENTE ---
        
        $pet_id = (int)($_POST['pet_id'] ?? 0);
        $servico_id = (int)($_POST['servico_id'] ?? 0); 
        $data_agendamento = $_POST['data_agendamento'] ?? '';
        $hora_agendamento = $_POST['hora_agendamento'] ?? '';
        $observacoes = $_POST['observacoes'] ?? '';
        $status = $_POST['status'] ?? 'agendado';
        
        $data_hora = $data_agendamento . ' ' . $hora_agendamento . ':00';
        
        $sql = "UPDATE agendamento SET 
                    pet_id = ?, 
                    servico_id = ?, 
                    data_agendamento = ?, 
                    observacoes = ?,
                    status = ?
                WHERE id = ?";

        $stmt = $pdo->prepare($sql);
        $stmt->execute([$pet_id, $servico_id, $data_hora, $observacoes, $status, $id]);

        $response['success'] = true;
        $response['message'] = "Agendamento #{$id} atualizado com sucesso!";

    } elseif ($acao === 'deletar' && $id > 0) {
        // --- AÇÃO: DELETAR AGENDAMENTO ---
        $sql = "DELETE FROM agendamento WHERE id = ?";
        $stmt = $pdo->prepare($sql);
        $stmt->execute([$id]);

        $response['success'] = true;
        $response['message'] = "Agendamento #{$id} excluído permanentemente!";

    } else {
        $response['message'] = 'Ação ou ID de agendamento inválidos.';
    }

} catch (\PDOException $e) {
    $response['message'] = 'Erro no banco de dados: ' . $e->getMessage();
    error_log("Erro em agendamento_processar: " . $e->getMessage());
}

echo json_encode($response);
?>