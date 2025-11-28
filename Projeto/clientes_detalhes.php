<?php
// Arquivo: clientes_detalhes.php
require_once 'conexao.php'; 

$cliente_id = filter_input(INPUT_GET, 'id', FILTER_VALIDATE_INT);
$cliente = null;
$pets = [];

if (isset($conexao) && $conexao && $cliente_id) {
    try {
        
        $sql_cliente = "SELECT id, nome, cpf, telefone, email, rua, numero, bairro, cep, complemento, data_nascimento 
                         FROM cliente
                         WHERE id = ? AND ativo = 1";
        $stmt_cliente = mysqli_prepare($conexao, $sql_cliente);
        mysqli_stmt_bind_param($stmt_cliente, "i", $cliente_id);
        mysqli_stmt_execute($stmt_cliente);
        $result_cliente = mysqli_stmt_get_result($stmt_cliente);
        $cliente = mysqli_fetch_assoc($result_cliente);
        mysqli_stmt_close($stmt_cliente);

        if ($cliente) {
            
            
            $sql_pets = "SELECT 
                            p.id, p.nome, p.data_nascimento, p.foto AS foto_path, 
                            r.nome AS raca_nome,
                            e.nome AS especie_nome 
                          FROM 
                            pet p
                          LEFT JOIN 
                            raca r ON p.raca_id = r.id
                          LEFT JOIN
                            especie e ON p.especie_id = e.id
                          WHERE 
                            p.cliente_id = ? 
                          ORDER BY 
                            p.nome ASC";
                            
            $stmt_pets = mysqli_prepare($conexao, $sql_pets);
            mysqli_stmt_bind_param($stmt_pets, "i", $cliente_id);
            mysqli_stmt_execute($stmt_pets);
            $result_pets = mysqli_stmt_get_result($stmt_pets);
            $pets = mysqli_fetch_all($result_pets, MYSQLI_ASSOC);
            mysqli_stmt_close($stmt_pets);
        }
    } catch (Exception $e) {
        
        error_log("Erro: " . $e->getMessage());
        echo '<div class="alert alert-danger">Erro técnico: ' . $e->getMessage() . '</div>';
    }
    
    if (isset($conexao)) mysqli_close($conexao);
}

if (!$cliente) {
    echo '<div class="alert alert-danger m-4">Cliente não encontrado.</div>';
    exit();
}


function formatar_cpf($cpf) {
    $cpf = preg_replace('/[^0-9]/', '', $cpf ?? '');
    return (strlen($cpf) === 11) ? vsprintf('%s%s%s.%s%s%s.%s%s%s-%s%s', str_split($cpf)) : $cpf;
}

function formatar_telefone($tel) {
    $tel = preg_replace('/[^0-9]/', '', $tel ?? '');
    $len = strlen($tel);
    if ($len === 11) return '(' . substr($tel, 0, 2) . ') ' . substr($tel, 2, 5) . '-' . substr($tel, 7, 4);
    if ($len === 10) return '(' . substr($tel, 0, 2) . ') ' . substr($tel, 2, 4) . '-' . substr($tel, 6, 4);
    return $tel;
}


function get_pet_icon($especie_nome) {
    $nome = mb_strtolower($especie_nome ?? ''); 
    
    if (strpos($nome, 'cão') !== false || strpos($nome, 'cao') !== false || strpos($nome, 'cachorro') !== false) {
        return '<i class="fas fa-dog"></i>';
    }
    if (strpos($nome, 'gato') !== false || strpos($nome, 'felino') !== false) {
        return '<i class="fas fa-cat"></i>';
    }
    if (strpos($nome, 'ave') !== false || strpos($nome, 'pássaro') !== false) {
        return '<i class="fas fa-dove"></i>'; 
    }
    if (strpos($nome, 'peixe') !== false) {
        return '<i class="fas fa-fish"></i>';
    }
    return '<i class="fas fa-paw"></i>'; 
}
?>

<style>
    .card-cliente { border-left: 5px solid #0d6efd; background-color: #f8f9fa; }
    
    .pet-card { 
        transition: transform 0.2s, box-shadow 0.2s; 
        border: none; 
        border-top: 4px solid #ccc; 
        background: #fff;
    }
    .pet-card:hover { 
        transform: translateY(-5px); 
        box-shadow: 0 10px 20px rgba(0,0,0,0.1) !important; 
    }
    
    
    .border-cao { border-color: #0dcaf0 !important; } 
    .border-gato { border-color: #d63384 !important; } 
    .border-ave { border-color: #ffc107 !important; } 
    .border-default { border-color: #6c757d !important; }

    .avatar-circle {
        width: 60px; height: 60px; background-color: #e9ecef; border-radius: 50%;
        display: flex; align-items: center; justify-content: center; font-size: 24px; color: #495057;
    }
</style>

<div class="container mt-4 pb-5">
    
    <div class="d-flex flex-column flex-md-row justify-content-between align-items-center mb-4 gap-3">
        <h2 class="text-primary fw-bold mb-0">
            <i class="fas fa-address-card me-2"></i> Ficha do Cliente
        </h2>
        
        <div class="btn-group shadow-sm">
            <a href="#" class="btn btn-outline-secondary item-menu-ajax" data-pagina="clientes_historico_completo.php?id=<?php echo $cliente_id; ?>">
                <i class="fas fa-history me-1"></i> Histórico
            </a>
            <a href="#" class="btn btn-success item-menu-ajax" data-pagina="pets_cadastro.php?cliente_id=<?php echo $cliente_id; ?>">
                <i class="fas fa-plus me-1"></i> Novo Pet
            </a>
        </div>
    </div>

    <div class="card shadow-sm mb-5 card-cliente">
        <div class="card-body p-4">
            <div class="row align-items-center">
                <div class="col-md-1 text-center mb-3 mb-md-0">
                    <div class="avatar-circle mx-auto shadow-sm">
                        <i class="fas fa-user"></i>
                    </div>
                </div>
                <div class="col-md-11">
                    <h4 class="mb-3 text-dark fw-bold"><?php echo htmlspecialchars($cliente['nome']); ?></h4>
                    <div class="row g-3">
                        <div class="col-md-4">
                            <small class="text-muted text-uppercase" style="font-size: 0.75rem;">CPF</small><br>
                            <strong><?php echo formatar_cpf($cliente['cpf']); ?></strong>
                        </div>
                        <div class="col-md-4">
                            <small class="text-muted text-uppercase" style="font-size: 0.75rem;">Contato</small><br>
                            <i class="fab fa-whatsapp text-success me-1"></i> 
                            <strong><?php echo formatar_telefone($cliente['telefone']); ?></strong>
                        </div>
                        <div class="col-md-4">
                            <small class="text-muted text-uppercase" style="font-size: 0.75rem;">Email</small><br>
                            <?php echo htmlspecialchars($cliente['email'] ?? '-'); ?>
                        </div>
                        <div class="col-12">
                            <hr class="my-2 text-muted opacity-25">
                            <small class="text-muted"><i class="fas fa-map-marker-alt me-1"></i></small> 
                            <span class="text-secondary">
                                <?php 
                                    $end = htmlspecialchars($cliente['rua'] ?? '') . ', ' . htmlspecialchars($cliente['numero'] ?? '');
                                    if (!empty($cliente['bairro'])) $end .= ' - ' . htmlspecialchars($cliente['bairro']);
                                    echo $end;
                                ?>
                            </span>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>

    <div class="d-flex align-items-center mb-3 border-bottom pb-2">
        <h4 class="mb-0 text-secondary"><i class="fas fa-paw me-2"></i>Pets</h4>
        <span class="badge bg-secondary ms-2 rounded-pill"><?php echo count($pets); ?></span>
    </div>

    <?php if (empty($pets)): ?>
        <div class="alert alert-light text-center border border-dashed p-5">
            <i class="fas fa-dog fa-3x text-muted mb-3 opacity-50"></i>
            <p class="text-muted">Este cliente ainda não tem pets cadastrados.</p>
            <a href="#" class="btn btn-sm btn-primary item-menu-ajax" data-pagina="pets_cadastro.php?cliente_id=<?php echo $cliente_id; ?>">
                Cadastrar o Primeiro Pet
            </a>
        </div>
    <?php else: ?>
        <div class="row g-4">
            <?php foreach ($pets as $pet): 
                
                $especie = $pet['especie_nome'] ?? 'Desconhecido';
                $raca = $pet['raca_nome'] ?? 'SRD (Sem Raça Definida)';
                $icon = get_pet_icon($especie);
                
                
                $borderColor = 'border-default';
                if (stripos($especie, 'cão') !== false) $borderColor = 'border-cao';
                elseif (stripos($especie, 'gato') !== false) $borderColor = 'border-gato';
                elseif (stripos($especie, 'ave') !== false) $borderColor = 'border-ave';
                
                
                $temFoto = !empty($pet['foto_path']) && file_exists($pet['foto_path']);
            ?>
            <div class="col-md-6 col-lg-4">
                <div class="card h-100 shadow-sm pet-card <?php echo $borderColor; ?>">
                    <div class="card-body position-relative">
                        
                        <span class="position-absolute top-0 end-0 badge bg-light text-dark border m-3 shadow-sm">
                            <?php echo $icon . ' ' . htmlspecialchars($especie); ?>
                        </span>

                        <div class="d-flex align-items-center mb-4 mt-2">
                            <div class="me-3 flex-shrink-0">
                                <?php if ($temFoto): ?>
                                    <img src="<?php echo htmlspecialchars($pet['foto_path']); ?>" alt="Foto" class="rounded-circle shadow-sm" style="width: 70px; height: 70px; object-fit: cover;">
                                <?php else: ?>
                                    <div class="rounded-circle bg-light d-flex justify-content-center align-items-center shadow-sm" style="width: 70px; height: 70px; border: 1px solid #eee;">
                                        <span class="fs-1 text-secondary opacity-50"><?php echo $icon; ?></span>
                                    </div>
                                <?php endif; ?>
                            </div>
                            
                            <div style="overflow: hidden;">
                                <h5 class="card-title mb-1 fw-bold text-dark text-truncate" title="<?php echo htmlspecialchars($pet['nome']); ?>">
                                    <?php echo htmlspecialchars($pet['nome']); ?>
                                </h5>
                                <small class="text-muted d-block text-truncate">
                                    <?php echo htmlspecialchars($raca); ?>
                                </small>
                                <?php if(!empty($pet['data_nascimento'])): ?>
                                    <small class="text-muted" style="font-size: 0.75rem;">
                                        <i class="fas fa-birthday-cake me-1"></i> 
                                        <?php echo date('d/m/Y', strtotime($pet['data_nascimento'])); ?>
                                    </small>
                                <?php endif; ?>
                            </div>
                        </div>

                        <div class="d-grid gap-2">
                            <a href="#" class="btn btn-sm btn-warning text-dark fw-bold item-menu-ajax" 
                               data-pagina="pets_carteira_vacinas.php?pet_id=<?php echo $pet['id']; ?>">
                                 <i class="fas fa-syringe me-1"></i> Vacinas
                            </a>
                            
                            <div class="row g-2">
                                <div class="col-6">
                                    <a href="#" class="btn btn-sm btn-outline-secondary w-100 item-menu-ajax" 
                                       data-pagina="pets_detalhes.php?id=<?php echo $pet['id']; ?>">
                                         <i class="fas fa-eye"></i> Detalhes
                                    </a>
                                </div>
                                <div class="col-6">
                                    <a href="#" class="btn btn-sm btn-outline-primary w-100 item-menu-ajax" 
                                       data-pagina="pets_editar.php?id=<?php echo $pet['id']; ?>">
                                         <i class="fas fa-pencil-alt"></i> Editar
                                    </a>
                                </div>
                            </div>
                        </div>

                    </div>
                </div>
            </div>
            <?php endforeach; ?>
        </div>
    <?php endif; ?>
</div>