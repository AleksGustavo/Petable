<?php
// Arquivo: clientes_detalhes.php - Ficha do Cliente e Lista de Pets
require_once 'conexao.php'; // Garante a conexão com o banco de dados

$cliente_id = filter_input(INPUT_GET, 'id', FILTER_VALIDATE_INT);
$cliente = null;
$pets = [];

if (isset($conexao) && $conexao && $cliente_id) {
    try {
        // 1. Busca os detalhes do Cliente
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
            // 2. Busca os Pets deste Cliente, usando JOIN para pegar o NOME DA RAÇA
            $sql_pets = "SELECT 
                            p.id, p.nome, p.data_nascimento, p.foto AS foto_path, 
                            r.nome AS raca_nome 
                         FROM 
                            pet p
                         LEFT JOIN 
                            raca r ON p.raca_id = r.id
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
            
            if (empty($pets)) {
                // Mensagem de log ou debug, não crítica
            }
        }
    } catch (Exception $e) {
        error_log("Erro ao carregar detalhes do cliente/pets: " . $e->getMessage());
        echo '<div class="alert alert-danger">Erro ao carregar detalhes do cliente: ' . htmlspecialchars($e->getMessage()) . '</div>';
    }
    
    // Fechar a conexão
    if (isset($conexao)) {
        mysqli_close($conexao);
    }
}

if (!$cliente) {
    echo '<div class="alert alert-danger">Cliente não encontrado ou ID inválido.</div>';
    exit();
}

// Funções auxiliares (incluídas para manter a consistência de formatação)
function formatar_cpf($cpf) {
    $cpf_limpo = preg_replace('/[^0-9]/', '', $cpf);
    if (strlen($cpf_limpo) === 11) {
        return substr($cpf_limpo, 0, 3) . '.' . substr($cpf_limpo, 3, 3) . '.' . substr($cpf_limpo, 6, 3) . '-' . substr($cpf_limpo, 9, 2);
    }
    return htmlspecialchars($cpf);
}

function formatar_telefone($tel) {
    $tel_limpo = preg_replace('/[^0-9]/', '', $tel);
    $len = strlen($tel_limpo);

    if ($len === 11) {
        return '(' . substr($tel_limpo, 0, 2) . ') ' . substr($tel_limpo, 2, 5) . '-' . substr($tel_limpo, 7, 4);
    } elseif ($len === 10) {
        return '(' . substr($tel_limpo, 0, 2) . ') ' . substr($tel_limpo, 2, 4) . '-' . substr($tel_limpo, 6, 4);
    }
    return htmlspecialchars($tel);
}
?>

<div class="container mt-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <h2><i class="fas fa-file-alt me-2"></i> Ficha do Cliente: <?php echo htmlspecialchars($cliente['nome']); ?></h2>
        
        <div class="d-flex flex-wrap gap-2">
            
            <a href="#" class="btn btn-warning item-menu-ajax" 
               data-pagina="clientes_historico_completo.php?id=<?php echo $cliente_id; ?>" 
               title="Ver histórico completo de atendimentos e compras">
                <i class="fas fa-history me-1"></i> Ver Histórico Completo
            </a>

            <a href="#" class="btn btn-primary item-menu-ajax" data-pagina="clientes_atualizar.php?id=<?php echo $cliente_id; ?>">
                <i class="fas fa-edit me-1"></i> Editar Cliente
            </a>
            <a href="#" class="btn btn-success item-menu-ajax" data-pagina="pets_cadastro.php?cliente_id=<?php echo $cliente_id; ?>">
                <i class="fas fa-plus me-1"></i> Cadastrar Pet
            </a>
        </div>
        </div>

    <div class="card shadow-sm mb-4">
        <div class="card-header bg-primary text-white">
            <h5 class="mb-0">Dados Pessoais e Contato</h5>
        </div>
        <div class="card-body">
            <div class="row">
                <div class="col-md-4"><strong>CPF:</strong> <?php echo formatar_cpf($cliente['cpf'] ?? 'N/A'); ?></div>
                <div class="col-md-4"><strong>Telefone:</strong> <?php echo formatar_telefone($cliente['telefone']); ?></div>
                <div class="col-md-4"><strong>Nascimento:</strong> <?php echo !empty($cliente['data_nascimento']) ? date('d/m/Y', strtotime($cliente['data_nascimento'])) : 'N/A'; ?></div>
            </div>
            <div class="row mt-2">
                <div class="col-12">
                    <strong>Email:</strong> <?php echo htmlspecialchars($cliente['email'] ?? 'N/A'); ?>
                </div>
                <div class="col-12 mt-2">
                    <strong>Endereço:</strong> 
                    <?php 
                        $endereco = htmlspecialchars($cliente['rua'] ?? '') . ', ' . 
                                     htmlspecialchars($cliente['numero'] ?? '') . 
                                     (empty($cliente['complemento']) ? '' : ' (' . htmlspecialchars($cliente['complemento']) . ')') . 
                                     (empty($cliente['bairro']) ? '' : ' - ' . htmlspecialchars($cliente['bairro'])) . 
                                     ' / CEP: ' . htmlspecialchars($cliente['cep'] ?? '');
                        echo !empty(trim($endereco, ', -/ ')) ? $endereco : 'Não cadastrado';
                    ?>
                </div>
            </div>
        </div>
    </div>
    
    <div class="card shadow-sm">
        <div class="card-header bg-success text-white">
            <h5 class="mb-0">Pets Cadastrados (<?php echo count($pets); ?>)</h5>
        </div>
        <div class="card-body">
            <?php if (empty($pets)): ?>
                <div class="alert alert-info text-center">
                    Este cliente não possui pets cadastrados.
                </div>
            <?php else: ?>
                <div class="list-group">
                    <?php foreach ($pets as $pet): ?>
                        <div class="list-group-item list-group-item-action d-flex justify-content-between align-items-center">
                            <div>
                                <a href="#" class="item-menu-ajax" data-pagina="pets_detalhes.php?id=<?php echo $pet['id']; ?>">
                                    <h6 class="mb-1 text-primary"><i class="fas fa-paw me-2"></i> <?php echo htmlspecialchars($pet['nome']); ?></h6>
                                </a>
                                <small class="text-muted">Raça: <?php echo htmlspecialchars($pet['raca_nome'] ?? 'N/A'); ?></small>
                            </div>
                            <div class="btn-group">
                                <a href="#" class="btn btn-sm btn-warning item-menu-ajax me-2" 
                                    data-pagina="pets_carteira_vacinas.php?pet_id=<?php echo $pet['id']; ?>" 
                                    title="Ver Histórico de Vacinas">
                                    <i class="fas fa-syringe me-1"></i> Ver Carteira
                                </a>
                                <a href="#" class="btn btn-sm btn-outline-primary item-menu-ajax" 
                                    data-pagina="pets_editar.php?id=<?php echo $pet['id']; ?>" 
                                    title="Editar Dados do Pet">
                                    <i class="fas fa-edit"></i> Editar Pet
                                </a>
                            </div>
                        </div>
                    <?php endforeach; ?>
                </div>
            <?php endif; ?>
        </div>
    </div>
</div>