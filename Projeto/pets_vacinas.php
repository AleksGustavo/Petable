<?php
require_once 'conexao.php'; 

$pet_id = filter_input(INPUT_GET, 'pet_id', FILTER_VALIDATE_INT);

if (!$pet_id) {
    echo '<div class="alert alert-danger m-4">ID do Pet inválido.</div>';
    exit();
}

$pet = null;
$vacinas = [];

if (isset($conexao)) {
    $sql_pet = "SELECT 
                    p.id, p.nome, p.foto, p.data_nascimento, 
                    c.id AS cliente_id, c.nome AS dono_nome,
                    e.nome AS especie_nome
                FROM pet p 
                LEFT JOIN cliente c ON p.cliente_id = c.id 
                LEFT JOIN especie e ON p.especie_id = e.id
                WHERE p.id = ?";
    
    $stmt = mysqli_prepare($conexao, $sql_pet);
    mysqli_stmt_bind_param($stmt, "i", $pet_id);
    mysqli_stmt_execute($stmt);
    $res_pet = mysqli_stmt_get_result($stmt);
    $pet = mysqli_fetch_assoc($res_pet);
    mysqli_stmt_close($stmt);

    if ($pet) {
        $sql_vacinas = "SELECT id, nome_vacina, data_aplicacao, data_proxima, veterinario, observacoes 
                        FROM carteira_vacina 
                        WHERE pet_id = ? 
                        ORDER BY data_aplicacao DESC";
        
        $stmt_v = mysqli_prepare($conexao, $sql_vacinas);
        mysqli_stmt_bind_param($stmt_v, "i", $pet_id);
        mysqli_stmt_execute($stmt_v);
        $res_vacinas = mysqli_stmt_get_result($stmt_v);
        $vacinas = mysqli_fetch_all($res_vacinas, MYSQLI_ASSOC);
        mysqli_stmt_close($stmt_v);
    }
}

$total_doses = count($vacinas);
$proxima_dose_data = null;
$proxima_dose_nome = 'N/D';
$proxima_dose_cor = 'secondary';
$proxima_dose_texto = 'Sem Reforços Pendentes';

$hoje = date('Y-m-d');
$proxima_vencimento = '9999-12-31';

foreach ($vacinas as $vacina) {
    if (!empty($vacina['data_proxima'])) {
        if ($vacina['data_proxima'] >= $hoje && $vacina['data_proxima'] < $proxima_vencimento) {
            $proxima_vencimento = $vacina['data_proxima'];
            $proxima_dose_data = $vacina['data_proxima'];
            $proxima_dose_nome = $vacina['nome_vacina'];
        }
        if ($vacina['data_proxima'] < $hoje) {
             $proxima_dose_data = $vacina['data_proxima'];
             $proxima_dose_nome = $vacina['nome_vacina'];
             $proxima_dose_cor = 'danger';
             $proxima_dose_texto = '⚠️ HÁ VACINA(S) VENCIDA(S)';
             break;
        }
    }
}

if ($proxima_dose_data && $proxima_dose_cor != 'danger') {
    $status_data = get_status_vacina($proxima_dose_data);
    $proxima_dose_cor = $status_data['cor'];
    $proxima_dose_texto = 'Vence em ' . date('d/m/Y', strtotime($proxima_dose_data));
}


function get_pet_icon($especie_nome) {
    $nome = mb_strtolower($especie_nome ?? '');
    if (strpos($nome, 'cão') !== false || strpos($nome, 'cachorro') !== false) return '<i class="fas fa-dog"></i>';
    if (strpos($nome, 'gato') !== false) return '<i class="fas fa-cat"></i>';
    if (strpos($nome, 'ave') !== false) return '<i class="fas fa-dove"></i>';
    return '<i class="fas fa-paw"></i>';
}

function get_status_vacina($data_proxima) {
    if (empty($data_proxima)) return ['cor' => 'secondary', 'texto' => 'Dose Única/Sem Reforço', 'icon' => 'fa-check-circle'];
    
    $hoje = date('Y-m-d');
    $data_limite = date('Y-m-d', strtotime('+30 days'));

    if ($data_proxima < $hoje) {
        return ['cor' => 'danger', 'texto' => 'Vencida', 'icon' => 'fa-exclamation-triangle'];
    } elseif ($data_proxima <= $data_limite) {
        return ['cor' => 'warning', 'texto' => 'Vence em breve', 'icon' => 'fa-clock'];
    } else {
        return ['cor' => 'success', 'texto' => 'Em dia', 'icon' => 'fa-shield-alt'];
    }
}

if ($pet) {
    $iconPet = get_pet_icon($pet['especie_nome']);
} else {
    echo '<div class="alert alert-danger m-4">Pet não encontrado no banco de dados.</div>';
    exit;
}
?>

<style>
    .timeline-section { position: relative; padding-left: 20px; }
    .timeline-section::before {
        content: ''; position: absolute; left: 0; top: 0; bottom: 0;
        width: 4px; background: #e9ecef; border-radius: 2px;
    }
    
    .vacina-card { 
        border-left: 5px solid #6c757d; 
        transition: all 0.3s ease; 
        position: relative; 
        margin-left: 20px !important; 
    }
    .vacina-card:hover { transform: translateX(5px); box-shadow: 0 5px 15px rgba(0,0,0,0.1); }
    
    .timeline-dot {
        position: absolute;
        left: -32px; 
        top: 25px; 
        width: 15px;
        height: 15px;
        border-radius: 50%;
        border: 3px solid white;
        z-index: 10;
        box-shadow: 0 0 0 2px #e9ecef;
    }

    .status-danger { border-left-color: #dc3545 !important; background-color: #fff5f5; }
    .status-warning { border-left-color: #ffc107 !important; background-color: #fffdf5; }
    .status-success { border-left-color: #198754 !important; background-color: #f0fff4; }
    .status-secondary { border-left-color: #6c757d !important; }

    .dot-danger { background-color: #dc3545; }
    .dot-warning { background-color: #ffc107; }
    .dot-success { background-color: #198754; }
    .dot-secondary { background-color: #6c757d; }

    .bg-gradient-pet { background: linear-gradient(45deg, #4e73df, #224abe); color: white; }
    .border-dashed { border-style: dashed !important; }
</style>

<div class="container mt-4 mb-5">
    
    <div class="d-flex justify-content-between align-items-center mb-4">
        <h2 class="fw-bold text-secondary">
            <i class="fas fa-notes-medical me-2"></i> Carteira Digital
        </h2>
        <a href="#" class="btn btn-outline-secondary item-menu-ajax" data-pagina="clientes_detalhes.php?id=<?php echo $pet['cliente_id']; ?>">
            <i class="fas fa-arrow-left me-2"></i> Voltar ao Cliente
        </a>
    </div>

    <div class="row">
        <div class="col-lg-4 mb-4">
            
            <div class="card shadow-sm mb-4 border-0">
                <div class="card-header bg-gradient-pet text-center py-4">
                    <div class="bg-white rounded-circle d-inline-flex justify-content-center align-items-center shadow-sm" style="width: 80px; height: 80px; font-size: 40px;">
                        <?php echo $iconPet; ?>
                    </div>
                    <h3 class="mt-3 mb-0 fw-bold"><?php echo htmlspecialchars($pet['nome']); ?></h3>
                    <small class="opacity-75">Proprietário: <?php echo htmlspecialchars($pet['dono_nome']); ?></small>
                </div>
                <div class="card-body">
                    <div class="d-flex justify-content-between border-bottom pb-2 mb-2">
                        <span class="text-muted">Espécie:</span>
                        <span class="fw-bold"><?php echo htmlspecialchars($pet['especie_nome'] ?? 'N/A'); ?></span>
                    </div>
                    <div class="d-flex justify-content-between">
                        <span class="text-muted">Nascimento:</span>
                        <span class="fw-bold"><?php echo !empty($pet['data_nascimento']) ? date('d/m/Y', strtotime($pet['data_nascimento'])) : '--/--/----'; ?></span>
                    </div>
                </div>
            </div>

            <div class="card shadow-sm border-0">
                <div class="card-header bg-white border-bottom fw-bold text-primary">
                    <i class="fas fa-plus-circle me-2"></i> Aplicar Nova Vacina
                </div>
                <div class="card-body bg-light">
                    <form action="vacinas_processar.php" method="POST" class="needs-validation">
                        <input type="hidden" name="pet_id" value="<?php echo $pet_id; ?>">
                        <input type="hidden" name="acao" value="inserir"> 

                        <div class="mb-3">
                            <label class="form-label small fw-bold text-uppercase">Vacina / Medicamento</label>
                            <div class="input-group">
                                <span class="input-group-text bg-white"><i class="fas fa-syringe text-primary"></i></span>
                                <input type="text" class="form-control" name="nome_vacina" placeholder="Ex: V10, Antirrábica" required>
                            </div>
                        </div>

                        <div class="row g-2 mb-3">
                            <div class="col-6">
                                <label class="form-label small fw-bold text-uppercase">Data Aplicação</label>
                                <input type="date" class="form-control" name="data_aplicacao" value="<?php echo date('Y-m-d'); ?>" required>
                            </div>
                            <div class="col-6">
                                <label class="form-label small fw-bold text-uppercase">Próx. Dose</label>
                                <input type="date" class="form-control" name="proximo_reforco">
                            </div>
                        </div>

                        <div class="mb-3">
                            <label class="form-label small fw-bold text-uppercase">Veterinário</label>
                            <div class="input-group">
                                <span class="input-group-text bg-white"><i class="fas fa-user-md text-info"></i></span>
                                <input type="text" class="form-control" name="veterinario" placeholder="Nome do Vet">
                            </div>
                        </div>

                        <div class="mb-3">
                            <label class="form-label small fw-bold text-uppercase">Observações</label>
                            <textarea class="form-control" name="observacoes" rows="2" placeholder="Lote, reação, etc."></textarea>
                        </div>

                        <button type="submit" class="btn btn-primary w-100 fw-bold shadow-sm">
                            <i class="fas fa-save me-2"></i> Registrar Aplicação
                        </button>
                    </form>
                </div>
            </div>
        </div>

        <div class="col-lg-8">
            <h5 class="mb-3 ms-2 text-muted">Histórico de Imunização</h5>
            
            <div class="card shadow-sm mb-4">
                <div class="card-body p-3">
                    <div class="row text-center">
                        <div class="col-6 border-end">
                            <h6 class="text-primary mb-0">TOTAL DE DOSES</h6>
                            <p class="fs-4 fw-bold mb-0 text-dark"><?php echo $total_doses; ?></p>
                        </div>
                        <div class="col-6">
                            <h6 class="text-primary mb-1">PRÓXIMO VENCIMENTO</h6>
                            <span class="badge bg-<?php echo $proxima_dose_cor; ?> p-2 fw-bold d-block">
                                <?php if ($proxima_dose_cor == 'danger'): ?>
                                    <?php echo $proxima_dose_texto; ?>
                                <?php elseif ($proxima_dose_data): ?>
                                    <?php echo htmlspecialchars($proxima_dose_nome); ?> | <?php echo date('d/m/Y', strtotime($proxima_dose_data)); ?>
                                <?php else: ?>
                                    <?php echo $proxima_dose_texto; ?>
                                <?php endif; ?>
                            </span>
                        </div>
                    </div>
                </div>
            </div>
            <?php if (empty($vacinas)): ?>
                <div class="alert alert-light border border-dashed text-center p-5 rounded-3">
                    <i class="fas fa-file-medical fa-3x text-muted mb-3 opacity-50"></i>
                    <h5 class="text-muted">Carteira em branco</h5>
                    <p class="mb-0">Nenhuma vacina registrada para este pet ainda.</p>
                </div>
            <?php else: ?>
                <div class="timeline-section">
                    <?php foreach ($vacinas as $vacina): 
                        $status = get_status_vacina($vacina['data_proxima']);
                    ?>
                    <div class="card vacina-card shadow-sm mb-3 status-<?php echo $status['cor']; ?>">
                        <div class="timeline-dot dot-<?php echo $status['cor']; ?>"></div>
                        
                        <div class="card-body">
                            <div class="d-flex justify-content-between align-items-start">
                                <div>
                                    <h5 class="card-title fw-bold text-dark mb-1">
                                        <?php echo htmlspecialchars($vacina['nome_vacina']); ?>
                                    </h5>
                                    
                                    <div class="mb-2">
                                        <span class="badge bg-<?php echo $status['cor']; ?> text-uppercase" style="font-size: 0.7rem;">
                                            <i class="fas <?php echo $status['icon']; ?> me-1"></i> <?php echo $status['texto']; ?>
                                        </span>
                                    </div>

                                    <div class="text-muted small">
                                        <i class="fas fa-calendar-check me-1 text-primary"></i> Aplicado em: 
                                        <strong><?php echo date('d/m/Y', strtotime($vacina['data_aplicacao'])); ?></strong>
                                    </div>

                                    <?php if (!empty($vacina['data_proxima'])): ?>
                                        <div class="text-muted small mt-1">
                                            <i class="fas fa-calendar-plus me-1 <?php echo ($status['cor'] == 'danger' ? 'text-danger' : 'text-warning'); ?>"></i> 
                                            Reforço: <strong><?php echo date('d/m/Y', strtotime($vacina['data_proxima'])); ?></strong>
                                        </div>
                                    <?php endif; ?>

                                    <?php if (!empty($vacina['veterinario'])): ?>
                                        <div class="text-muted small mt-1 fst-italic">
                                            <i class="fas fa-user-md me-1"></i> Vet: <?php echo htmlspecialchars($vacina['veterinario']); ?>
                                        </div>
                                    <?php endif; ?>
                                    
                                    <?php if (!empty($vacina['observacoes'])): ?>
                                        <div class="mt-2 p-2 bg-white rounded border small text-secondary">
                                            <?php echo htmlspecialchars($vacina['observacoes']); ?>
                                        </div>
                                    <?php endif; ?>
                                </div>

                                <div class="d-flex flex-column gap-2">
                                    <a href="#" class="btn btn-sm btn-outline-secondary border-0 text-success" title="Editar registro">
                                        <i class="fas fa-pencil-alt"></i>
                                    </a>
                                    <a href="#" onclick="if(confirm('Tem certeza que deseja excluir?')) { /* logica de exclusão */ }" class="btn btn-sm btn-outline-danger border-0" title="Excluir registro">
                                        <i class="fas fa-trash-alt"></i>
                                    </a>
                                </div>
                            </div>
                        </div>
                    </div>
                    <?php endforeach; ?>
                </div>
            <?php endif; ?>
        </div>
    </div>
</div>