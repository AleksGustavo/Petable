<?php
// Arquivo: pets_vacinas.php
require_once 'conexao.php'; 

$pet_id = $_GET['pet_id'] ?? null;
$pet = null;
$vacinas_cadastradas = [];

if (!$pet_id) {
    echo '<div class="alert alert-danger">ID do Pet não fornecido para gerenciar vacinas.</div>';
    exit();
}

try {
    // 1. Carrega dados básicos do Pet
    $stmt_pet = $pdo->prepare("SELECT p.nome AS nome_pet, c.nome AS nome_cliente FROM pet p JOIN cliente c ON p.cliente_id = c.id WHERE p.id = ?");
    $stmt_pet->execute([$pet_id]);
    $pet = $stmt_pet->fetch(PDO::FETCH_ASSOC);

    if (!$pet) {
        echo '<div class="alert alert-danger">Pet não encontrado.</div>';
        exit();
    }

    // 2. Carrega as vacinas registradas para este pet (se você tiver uma tabela de vacinas_pet)
    // EXEMBRO: Se você tiver uma tabela 'vacinas_pet' com id, pet_id, nome_vacina, data_aplicacao, proximo_reforco
    // $stmt_vacinas = $pdo->prepare("SELECT * FROM vacinas_pet WHERE pet_id = ? ORDER BY data_aplicacao DESC");
    // $stmt_vacinas->execute([$pet_id]);
    // $vacinas_cadastradas = $stmt_vacinas->fetchAll(PDO::FETCH_ASSOC);

} catch (PDOException $e) {
    error_log("Erro ao carregar dados de vacinas do pet: " . $e->getMessage());
    echo '<div class="alert alert-danger">Erro ao carregar informações de vacinas: ' . htmlspecialchars($e->getMessage()) . '</div>';
    exit();
}
?>

<div class="d-flex justify-content-between align-items-center mb-4">
    <h2><i class="fas fa-syringe me-2"></i> Carteira de Vacinas de <?= htmlspecialchars($pet['nome_pet']) ?></h2>
    <a href="#" class="btn btn-secondary item-menu-ajax" data-pagina="clientes_listar.php">
        <i class="fas fa-arrow-left me-2"></i> Voltar para Busca
    </a>
</div>

<div id="status-message-area"></div>

<div class="card shadow-sm mb-4">
    <div class="card-body">
        <h5 class="card-title">Informações do Pet</h5>
        <p class="mb-1"><strong>Nome:</strong> <?= htmlspecialchars($pet['nome_pet']) ?></p>
        <p class="mb-0"><strong>Dono:</strong> <?= htmlspecialchars($pet['nome_cliente']) ?></p>
    </div>
</div>

<div class="card shadow-sm">
    <div class="card-header bg-primary text-white">
        <i class="fas fa-plus-circle me-2"></i> Registrar Nova Vacina
    </div>
    <div class="card-body">
        <form id="form-registrar-vacina" action="vacinas_processar.php" method="POST">
            <input type="hidden" name="pet_id" value="<?= htmlspecialchars($pet_id) ?>">
            
            <div class="row g-3">
                <div class="col-md-6">
                    <label for="nome_vacina" class="form-label">Nome da Vacina <span class="text-danger">*</span></label>
                    <input type="text" class="form-control" id="nome_vacina" name="nome_vacina" required>
                </div>
                <div class="col-md-3">
                    <label for="data_aplicacao" class="form-label">Data de Aplicação <span class="text-danger">*</span></label>
                    <input type="date" class="form-control" id="data_aplicacao" name="data_aplicacao" required>
                </div>
                <div class="col-md-3">
                    <label for="proximo_reforco" class="form-label">Próximo Reforço</label>
                    <input type="date" class="form-control" id="proximo_reforco" name="proximo_reforco">
                </div>
                <div class="col-12 mt-4">
                    <button type="submit" class="btn btn-success">
                        <i class="fas fa-save me-2"></i> Registrar Vacina
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>

<div class="card shadow-sm mt-4">
    <div class="card-header bg-info text-white">
        <i class="fas fa-list-alt me-2"></i> Histórico de Vacinas
    </div>
    <div class="card-body">
        <?php if (empty($vacinas_cadastradas)): ?>
            <p class="text-muted">Nenhuma vacina registrada para este pet.</p>
        <?php else: ?>
            <div class="table-responsive">
                <table class="table table-striped table-hover table-sm">
                    <thead class="table-light">
                        <tr>
                            <th>Vacina</th>
                            <th>Data Aplicação</th>
                            <th>Próximo Reforço</th>
                            <th style="width: 120px;">Ações</th>
                        </tr>
                    </thead>
                    <tbody>
                        <?php foreach ($vacinas_cadastradas as $vacina): ?>
                            <tr>
                                <td><?= htmlspecialchars($vacina['nome_vacina']) ?></td>
                                <td><?= date('d/m/Y', strtotime($vacina['data_aplicacao'])) ?></td>
                                <td>
                                    <?php if ($vacina['proximo_reforco']): ?>
                                        <?= date('d/m/Y', strtotime($vacina['proximo_reforco'])) ?>
                                    <?php else: ?>
                                        N/D
                                    <?php endif; ?>
                                </td>
                                <td>
                                    <button class="btn btn-sm btn-outline-primary me-1" title="Editar Vacina"><i class="fas fa-edit"></i></button>
                                    <button class="btn btn-sm btn-outline-danger" title="Excluir Vacina"><i class="fas fa-trash-alt"></i></button>
                                </td>
                            </tr>
                        <?php endforeach; ?>
                    </tbody>
                </table>
            </div>
        <?php endif; ?>
    </div>
</div>