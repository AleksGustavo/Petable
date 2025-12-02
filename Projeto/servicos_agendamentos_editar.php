<?php
// servicos_agendamentos_editar.php

// Inclua o arquivo de conexão com o banco de dados
require_once 'conexao.php'; // Certifique-se de que este arquivo inicializa a variável $pdo

if (!isset($_GET['id']) || empty($_GET['id'])) {
    echo '<div class="alert alert-danger">ID de agendamento não fornecido para edição.</div>';
    exit;
}

$id_agendamento = (int)$_GET['id'];
$dados_agendamento = [];
$servicos_agendados = [];

try {
    // 1. Conectar ao banco (Assumindo que $pdo é a sua conexão PDO)
    // Se a conexão não for global, chame sua função aqui: $pdo = conectar_ao_banco();

    // 2. Buscar os dados principais do agendamento (Tabela 'agendamento')
    // ASSUME-SE: que a tabela 'agendamento' tem campos como 'id', 'nome_pet', 'nome_cliente', 'data_agendamento', 'hora_agendamento', 'status'
    $stmt_agendamento = $pdo->prepare("SELECT * FROM agendamento WHERE id = :id");
    $stmt_agendamento->bindParam(':id', $id_agendamento, PDO::PARAM_INT);
    $stmt_agendamento->execute();
    $dados_agendamento = $stmt_agendamento->fetch(PDO::FETCH_ASSOC);

    if (!$dados_agendamento) {
        echo '<div class="alert alert-warning">Agendamento não encontrado.</div>';
        exit;
    }

    // 3. Buscar os serviços relacionados (Tabela 'agendamento_servico' e JOIN com 'servico')
    // ASSUME-SE: que a tabela 'servico' tem um campo 'nome_servico'
    $stmt_servicos = $pdo->prepare("
        SELECT 
            s.id as servico_id, 
            s.nome as nome_servico
        FROM agendamento_servico AS ags
        JOIN servico AS s ON ags.servico_id = s.id
        WHERE ags.agendamento_id = :id_agendamento
    ");
    $stmt_servicos->bindParam(':id_agendamento', $id_agendamento, PDO::PARAM_INT);
    $stmt_servicos->execute();
    $servicos_agendados = $stmt_servicos->fetchAll(PDO::FETCH_ASSOC);

    // 4. Buscar a lista completa de serviços para o SELECT de opções (ASSUME-SE a tabela 'servico')
    $stmt_todos_servicos = $pdo->query("SELECT id, nome FROM servico ORDER BY nome ASC");
    $todos_servicos = $stmt_todos_servicos->fetchAll(PDO::FETCH_ASSOC);

} catch (PDOException $e) {
    // error_log("Erro ao carregar agendamento: " . $e->getMessage()); 
    echo '<div class="alert alert-danger">Erro ao carregar dados do agendamento.</div>';
    exit;
}
?>

<div class="container mt-4">
    <h2><i class="fas fa-edit me-2"></i> Editar Agendamento #<?php echo $dados_agendamento['id']; ?></h2>
    <hr>
    
    <form id="form-editar-agendamento" action="servicos_agendamentos_salvar_edicao.php" method="POST">
        
        <input type="hidden" name="id_agendamento" value="<?php echo $dados_agendamento['id']; ?>">

        <div class="row g-3">
            <div class="col-md-6">
                <label for="nome_pet" class="form-label">Nome do Pet</label>
                <input type="text" class="form-control" id="nome_pet" name="nome_pet" 
                       value="<?php echo htmlspecialchars($dados_agendamento['nome_pet'] ?? ''); ?>" required>
            </div>
            <div class="col-md-6">
                <label for="nome_cliente" class="form-label">Nome do Cliente</label>
                <input type="text" class="form-control" id="nome_cliente" name="nome_cliente" 
                       value="<?php echo htmlspecialchars($dados_agendamento['nome_cliente'] ?? ''); ?>" required>
            </div>
        </div>

        <div class="row g-3 mt-2">
            <div class="col-md-4">
                <label for="data_agendamento" class="form-label">Data</label>
                <input type="date" class="form-control" id="data_agendamento" name="data_agendamento" 
                       value="<?php echo $dados_agendamento['data_agendamento'] ?? ''; ?>" required>
            </div>
            <div class="col-md-4">
                <label for="hora_agendamento" class="form-label">Hora</label>
                <input type="time" class="form-control" id="hora_agendamento" name="hora_agendamento" 
                       value="<?php echo $dados_agendamento['hora_agendamento'] ?? ''; ?>" required>
            </div>
            <div class="col-md-4">
                <label for="status" class="form-label">Status</label>
                <select class="form-select" id="status" name="status" required>
                    <option value="agendado" <?php echo (($dados_agendamento['status'] ?? '') == 'agendado' ? 'selected' : ''); ?>>Agendado</option>
                    <option value="confirmado" <?php echo (($dados_agendamento['status'] ?? '') == 'confirmado' ? 'selected' : ''); ?>>Confirmado</option>
                    <option value="concluido" <?php echo (($dados_agendamento['status'] ?? '') == 'concluido' ? 'selected' : ''); ?>>Concluído</option>
                    <option value="cancelado" <?php echo (($dados_agendamento['status'] ?? '') == 'cancelado' ? 'selected' : ''); ?>>Cancelado</option>
                </select>
            </div>
        </div>
        
        <div class="row g-3 mt-2">
            <div class="col-12">
                <label for="servicos" class="form-label">Serviços Agendados</label>
                <select multiple class="form-select" id="servicos" name="servicos[]" required>
                    <?php 
                    $servicos_agendados_ids = array_column($servicos_agendados, 'servico_id');
                    foreach ($todos_servicos as $servico) {
                        $selected = in_array($servico['id'], $servicos_agendados_ids) ? 'selected' : '';
                        echo "<option value=\"{$servico['id']}\" {$selected}>" . htmlspecialchars($servico['nome']) . "</option>";
                    }
                    ?>
                </select>
                <div class="form-text">Mantenha 'Ctrl' (Windows) ou 'Cmd' (Mac) pressionado para selecionar múltiplos serviços.</div>
            </div>
        </div>

        <div class="mt-4">
            <button type="submit" class="btn btn-primary me-2"><i class="fas fa-save me-1"></i> Salvar Edição</button>
            <a href="#" class="btn btn-secondary item-menu-ajax" data-pagina="servicos_agendamentos_listar.php">
                <i class="fas fa-arrow-left me-1"></i> Voltar
            </a>
        </div>
    </form>
</div>

<script>
$(document).ready(function() {
    $('#form-editar-agendamento').on('submit', function(e) {
        e.preventDefault();
        
        const $form = $(this);
        const url_salvar = $form.attr('action');
        
        $.ajax({
            url: url_salvar,
            method: 'POST',
            data: $form.serialize(), 
            dataType: 'json',
            success: function(response) {
                if (response.sucesso) {
                    alert('Agendamento atualizado com sucesso!');
                    // Redireciona de volta para a listagem (ajuste 'sua_pagina_listagem.php')
                    $('.item-menu-ajax[data-pagina="sua_pagina_listagem.php"]').trigger('click');
                } else {
                    alert('Erro ao salvar: ' + response.mensagem);
                }
            },
            error: function() {
                alert('Erro de conexão ao salvar a edição.');
            }
        });
    });
});
</script>