<?php
// servicos_agendamentos_salvar_edicao.php

require_once 'conexao.php'; 

header('Content-Type: application/json');

// Campos mínimos de validação (ajuste conforme sua tabela 'agendamento')
if (!isset($_POST['id_agendamento'], $_POST['nome_pet'], $_POST['data_agendamento'], $_POST['status'])) {
    echo json_encode(['sucesso' => false, 'mensagem' => 'Dados de agendamento incompletos.']);
    exit;
}

$id = (int)$_POST['id_agendamento'];
$nome_pet = $_POST['nome_pet'];
$nome_cliente = $_POST['nome_cliente'];
$data = $_POST['data_agendamento'];
$hora = $_POST['hora_agendamento'];
$status = $_POST['status']; 
$servicos_selecionados = isset($_POST['servicos']) && is_array($_POST['servicos']) ? $_POST['servicos'] : [];

try {
    // 1. Inicia a transação para garantir que ambas as tabelas sejam atualizadas corretamente
    $pdo->beginTransaction();

    // 2. Atualiza a tabela principal 'agendamento'
    // ASSUME-SE que estes campos existem na sua tabela 'agendamento'
    $stmt_agendamento = $pdo->prepare("UPDATE agendamento SET 
        nome_pet = :nome_pet, 
        nome_cliente = :nome_cliente, 
        data_agendamento = :data, 
        hora_agendamento = :hora, 
        status = :status 
        WHERE id = :id");

    $stmt_agendamento->bindParam(':id', $id, PDO::PARAM_INT);
    $stmt_agendamento->bindParam(':nome_pet', $nome_pet);
    $stmt_agendamento->bindParam(':nome_cliente', $nome_cliente);
    $stmt_agendamento->bindParam(':data', $data);
    $stmt_agendamento->bindParam(':hora', $hora);
    $stmt_agendamento->bindParam(':status', $status);
    $stmt_agendamento->execute();
    
    // 3. Sincroniza a tabela de relacionamento 'agendamento_servico'

    // a) Remove todos os serviços antigos (ON DELETE CASCADE não se aplica aqui, pois estamos deletando na tabela de relacionamento)
    $stmt_delete = $pdo->prepare("DELETE FROM agendamento_servico WHERE agendamento_id = :id");
    $stmt_delete->bindParam(':id', $id, PDO::PARAM_INT);
    $stmt_delete->execute();
    
    // b) Insere os novos serviços selecionados
    $stmt_insert = $pdo->prepare("INSERT INTO agendamento_servico (agendamento_id, servico_id) VALUES (:agendamento_id, :servico_id)");

    foreach ($servicos_selecionados as $servico_id) {
        // Validação básica para garantir que o ID é um número
        $servico_id_int = (int)$servico_id; 
        
        $stmt_insert->bindParam(':agendamento_id', $id, PDO::PARAM_INT);
        $stmt_insert->bindParam(':servico_id', $servico_id_int, PDO::PARAM_INT);
        $stmt_insert->execute();
    }
    
    // 4. Se tudo deu certo, confirma as alterações no banco de dados
    $pdo->commit();
    echo json_encode(['sucesso' => true]);

} catch (PDOException $e) {
    // 5. Em caso de erro, desfaz todas as alterações
    $pdo->rollBack();
    // error_log("Erro PDO ao salvar edição: " . $e->getMessage()); 
    echo json_encode(['sucesso' => false, 'mensagem' => 'Erro interno do servidor ao salvar: ' . $e->getMessage()]);
} catch (Exception $e) {
    $pdo->rollBack();
    echo json_encode(['sucesso' => false, 'mensagem' => 'Erro desconhecido ao salvar.']);
}
?>