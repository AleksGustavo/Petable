<?php
require_once 'conexao.php';

$pet_id = filter_input(INPUT_GET, 'pet_id', FILTER_VALIDATE_INT);
$pet_info = ['nome' => 'Pet Desconhecido', 'cliente_nome' => '', 'cliente_id' => 0];
$vacinas = [];

if (isset($conexao) && $conexao && $pet_id) {
    try {
        $sql_info = "SELECT p.nome, c.nome AS cliente_nome, c.id AS cliente_id
                     FROM pet p
                     JOIN cliente c ON p.cliente_id = c.id
                     WHERE p.id = ?";
        $stmt_info = mysqli_prepare($conexao, $sql_info);
        mysqli_stmt_bind_param($stmt_info, "i", $pet_id);
        mysqli_stmt_execute($stmt_info);
        $result_info = mysqli_stmt_get_result($stmt_info);
        $pet_info_temp = mysqli_fetch_assoc($result_info);
        mysqli_stmt_close($stmt_info);

        if ($pet_info_temp) {
            $pet_info = $pet_info_temp;
        } else {
             echo '<div class="alert alert-danger">Pet não encontrado.</div>';
             exit();
        }

        $sql_vacinas = "SELECT * FROM carteira_vacina WHERE pet_id = ? ORDER BY data_aplicacao DESC";
        $stmt_vacinas = mysqli_prepare($conexao, $sql_vacinas);
        mysqli_stmt_bind_param($stmt_vacinas, "i", $pet_id);
        mysqli_stmt_execute($stmt_vacinas);
        $result_vacinas = mysqli_stmt_get_result($stmt_vacinas);
        $vacinas = mysqli_fetch_all($result_vacinas, MYSQLI_ASSOC);
        mysqli_stmt_close($stmt_vacinas);
        
    } catch (Exception $e) {
        error_log("Erro ao carregar carteira de vacinas: " . $e->getMessage());
        echo '<div class="alert alert-danger">Erro ao carregar o histórico: Tente novamente.</div>';
    }
} else {
    echo '<div class="alert alert-warning">ID do Pet inválido ou não fornecido.</div>';
    exit();
}
?>

<div class="container mt-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <h2><i class="fas fa-file-invoice me-2"></i> Carteira de Vacinas - <?php echo htmlspecialchars($pet_info['nome']); ?></h2>
        
        <button type="button" class="btn btn-primary" data-bs-toggle="modal" data-bs-target="#modalAdicionarVacina">
            <i class="fas fa-syringe me-1"></i> Adicionar Vacina (Manual)
        </button>
    </div>

    <p class="mb-3 text-muted">
        Dono: 
        <a href="#" class="item-menu-ajax" data-pagina="clientes_detalhes.php?id=<?php echo $pet_info['cliente_id']; ?>">
            <?php echo htmlspecialchars($pet_info['cliente_nome']); ?>
        </a>
        <span class="ms-3">|</span>
        <a href="#" class="item-menu-ajax ms-3" data-pagina="pets_detalhes.php?id=<?php echo $pet_id; ?>">
            <i class="fas fa-arrow-left"></i> Voltar para Ficha do Pet
        </a>
    </p>

    <?php if (empty($vacinas)): ?>
        <div class="alert alert-info text-center">Nenhuma vacina registrada para este Pet.</div>
    <?php else: ?>
        <div class="table-responsive">
            <table class="table table-striped table-hover shadow-sm">
                <thead class="table-dark">
                    <tr>
                        <th>Nome da Vacina</th>
                        <th>Data Aplicação</th>
                        <th>Próxima Dose</th>
                        <th>Veterinário</th>
                        <th>Observações</th>
                    </tr>
                </thead>
                <tbody>
                    <?php foreach ($vacinas as $v): ?>
                    <tr>
                        <td><?php echo htmlspecialchars($v['nome_vacina']); ?></td>
                        <td><?php echo date('d/m/Y', strtotime($v['data_aplicacao'])); ?></td>
                        <td>
                            <?php 
                                if (!empty($v['data_proxima']) && $v['data_proxima'] !== '0000-00-00') {
                                    $data_prox = date('d/m/Y', strtotime($v['data_proxima']));
                                    
                                    if (strtotime($v['data_proxima']) < time()) {
                                        echo '<span class="badge bg-danger">Vencida: ' . $data_prox . '</span>';
                                    } elseif (strtotime($v['data_proxima']) < strtotime('+30 days')) {
                                        echo '<span class="badge bg-warning text-dark">Próxima em: ' . $data_prox . '</span>';
                                    } else {
                                        echo $data_prox;
                                    }
                                } else {
                                    echo '<span class="text-muted">N/A</span>';
                                }
                            ?>
                        </td>
                        <td><?php echo htmlspecialchars($v['veterinario'] ?? 'N/A'); ?></td>
                        <td><?php echo htmlspecialchars($v['observacoes'] ?? ''); ?></td>
                    </tr>
                    <?php endforeach; ?>
                </tbody>
            </table>
        </div>
    <?php endif; ?>
</div>

<div class="modal fade" id="modalAdicionarVacina" tabindex="-1" aria-labelledby="modalLabelVacina" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header bg-primary text-white">
                <h5 class="modal-title" id="modalLabelVacina">Registrar Vacina para <?php echo htmlspecialchars($pet_info['nome']); ?></h5>
                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
            </div>
            <form id="form-add-vacina" action="pets_processar_vacina_manual.php" method="POST"> 
                <div class="modal-body">
                    <input type="hidden" name="pet_id" value="<?php echo $pet_id; ?>">
                    
                    <div class="mb-3">
                        <label for="nome_vacina" class="form-label">Nome da Vacina <span class="text-danger">*</span></label>
                        <input type="text" class="form-control" id="nome_vacina" name="nome_vacina" required>
                    </div>
                    <div class="mb-3">
                        <label for="data_aplicacao" class="form-label">Data da Aplicação <span class="text-danger">*</span></label>
                        <input type="date" class="form-control" id="data_aplicacao" name="data_aplicacao" required value="<?php echo date('Y-m-d'); ?>">
                    </div>
                    <div class="mb-3">
                        <label for="data_proxima" class="form-label">Data da Próxima Dose (Previsão)</label>
                        <input type="date" class="form-control" id="data_proxima" name="data_proxima">
                    </div>
                    <div class="mb-3">
                        <label for="veterinario" class="form-label">Nome do Veterinário</label>
                        <input type="text" class="form-control" id="veterinario" name="veterinario">
                    </div>
                    <div class="mb-3">
                        <label for="observacoes" class="form-label">Observações</label>
                        <textarea class="form-control" id="observacoes" name="observacoes" rows="3"></textarea>
                    </div>
                </div>
                <div class="modal-footer">
                    <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Cancelar</button>
                    <button type="submit" class="btn btn-primary" id="btn-salvar-vacina"><i class="fas fa-save me-1"></i> Salvar Registro</button> 
                </div>
            </form>
        </div>
    </div>
</div>

<script>
$(document).ready(function() {
    $('#form-add-vacina').on('submit', function(e) {
        e.preventDefault();

        var form = $(this);
        var url = form.attr('action');
        var data = form.serialize(); 

        var btn = $('#btn-salvar-vacina');
        var original_text = btn.html();

        btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin me-1"></i> Salvando...');

        $.ajax({
            type: 'POST',
            url: url,
            data: data,
            dataType: 'json',
            success: function(response) {
                if (response.success) {
                    
                    alert('SUCESSO! ' + response.message); 
                    
                    $('#modalAdicionarVacina').modal('hide');
                    
                    window.location.reload(); 

                } else {
                    alert('ERRO! ' + response.message);
                }
            },
            error: function(xhr, status, error) {
                alert('Erro de comunicação com o servidor. Status: ' + status + '. Tente novamente.');
            },
            complete: function() {
                btn.prop('disabled', false).html(original_text);
            }
        });
    });
});
</script>