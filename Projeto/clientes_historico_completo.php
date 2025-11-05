<?php
// Arquivo: clientes_historico_completo.php - Visão Completa, Pets e Histórico de Atendimentos

// 1. Configuração e Conexão
require_once 'conexao.php'; // Garante a conexão com o banco de dados

// Define o ID do cliente a partir da URL
$cliente_id = filter_input(INPUT_GET, 'id', FILTER_VALIDATE_INT);
$cliente = null;
$pets = [];
$historico_atendimentos = [];

if (!$cliente_id) {
    echo '<div class="container mt-4"><div class="alert alert-danger">ID do cliente inválido.</div></div>';
    exit();
}

if (isset($conexao) && $conexao) {
    try {
        // --- 1. Busca os detalhes do Cliente ---
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
            // --- 2. Busca os Pets deste Cliente ---
            // Usamos JOIN para pegar o NOME DA RAÇA
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

            // --- 3. Busca o Histórico Geral de Compras/Serviços (Atendimentos) ---
            // ASSUMINDO: A tabela 'atendimento' contém registros de serviços ou vendas
            // É feito um JOIN com a tabela 'pet' para mostrar qual pet estava envolvido, se houver.
            $sql_historico = "SELECT 
                                a.data_atendimento, a.tipo_servico, a.valor_total, a.observacoes,
                                p.nome AS pet_nome
                              FROM 
                                atendimento a
                              LEFT JOIN
                                pet p ON a.pet_id = p.id
                              WHERE 
                                a.cliente_id = ?
                              ORDER BY
                                a.data_atendimento DESC, a.id DESC
                              LIMIT 50"; // Limita para não sobrecarregar a tela
            
            $stmt_historico = mysqli_prepare($conexao, $sql_historico);
            mysqli_stmt_bind_param($stmt_historico, "i", $cliente_id);
            mysqli_stmt_execute($stmt_historico);
            $result_historico = mysqli_stmt_get_result($stmt_historico);
            $historico_atendimentos = mysqli_fetch_all($result_historico, MYSQLI_ASSOC);
            mysqli_stmt_close($stmt_historico);

        }
    } catch (Exception $e) {
        error_log("Erro ao carregar histórico completo do cliente: " . $e->getMessage());
        echo '<div class="container mt-4"><div class="alert alert-danger">Erro ao carregar detalhes: ' . htmlspecialchars($e->getMessage()) . '</div></div>';
        exit();
    } finally {
        // Fecha a conexão após todas as operações
        if (isset($conexao)) {
            mysqli_close($conexao);
        }
    }
}

if (!$cliente) {
    echo '<div class="container mt-4"><div class="alert alert-danger">Cliente não encontrado ou ID inválido.</div></div>';
    exit();
}

// Função auxiliar para formatar endereço
function formatar_endereco($cliente) {
    $endereco = htmlspecialchars($cliente['rua'] ?? '') . ', ' . 
                htmlspecialchars($cliente['numero'] ?? '') . 
                (empty($cliente['complemento']) ? '' : ' (' . htmlspecialchars($cliente['complemento']) . ')') . 
                (empty($cliente['bairro']) ? '' : ' - ' . htmlspecialchars($cliente['bairro'])) . 
                ' / CEP: ' . htmlspecialchars($cliente['cep'] ?? '');
    return !empty(trim($endereco, ', -/ ')) ? $endereco : 'Não cadastrado';
}

// Função auxiliar para formatar CPF
function formatar_cpf($cpf) {
    $cpf_limpo = preg_replace('/[^0-9]/', '', $cpf);
    if (strlen($cpf_limpo) === 11) {
        return substr($cpf_limpo, 0, 3) . '.' . substr($cpf_limpo, 3, 3) . '.' . substr($cpf_limpo, 6, 3) . '-' . substr($cpf_limpo, 9, 2);
    }
    return htmlspecialchars($cpf);
}

// Função auxiliar para formatar Telefone (exemplo: (99) 99999-9999)
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
    <div class="d-flex justify-content-between align-items-center mb-4 border-bottom pb-2">
        <h2 class="text-primary"><i class="fas fa-history me-2"></i> Histórico Completo de <?php echo htmlspecialchars($cliente['nome']); ?></h2>
        <a href="#" class="btn btn-secondary item-menu-ajax" data-pagina="clientes_detalhes.php?id=<?php echo $cliente_id; ?>">
            <i class="fas fa-arrow-left me-1"></i> Voltar para Ficha
        </a>
    </div>

    <!-- Seção 1: Detalhes do Cliente -->
    <div class="card shadow-sm mb-4">
        <div class="card-header bg-dark text-white">
            <h5 class="mb-0">Dados Pessoais</h5>
        </div>
        <div class="card-body">
            <div class="row">
                <div class="col-md-4"><strong>CPF:</strong> <?php echo formatar_cpf($cliente['cpf'] ?? 'N/A'); ?></div>
                <div class="col-md-4"><strong>Telefone:</strong> <?php echo formatar_telefone($cliente['telefone']); ?></div>
                <div class="col-md-4"><strong>Nascimento:</strong> <?php echo !empty($cliente['data_nascimento']) ? date('d/m/Y', strtotime($cliente['data_nascimento'])) : 'N/A'; ?></div>
            </div>
            <div class="row mt-2">
                <div class="col-md-6"><strong>Email:</strong> <?php echo htmlspecialchars($cliente['email'] ?? 'N/A'); ?></div>
                <div class="col-md-6"><strong>Endereço:</strong> <?php echo formatar_endereco($cliente); ?></div>
            </div>
        </div>
    </div>
    
    <!-- Seção 2: Pets do Cliente -->
    <div class="card shadow-sm mb-4">
        <div class="card-header bg-info text-white">
            <h5 class="mb-0"><i class="fas fa-paw me-2"></i> Pets Cadastrados (<?php echo count($pets); ?>)</h5>
        </div>
        <div class="card-body">
            <?php if (empty($pets)): ?>
                <div class="alert alert-warning text-center">Este cliente não possui pets cadastrados.</div>
            <?php else: ?>
                <div class="d-flex flex-wrap gap-3">
                    <?php foreach ($pets as $pet): ?>
                        <span class="badge bg-secondary p-2 shadow-sm">
                            <i class="fas fa-dog me-1"></i> 
                            <?php echo htmlspecialchars($pet['nome']); ?> 
                            (<?php echo htmlspecialchars($pet['raca_nome'] ?? 'Sem Raça'); ?>)
                        </span>
                    <?php endforeach; ?>
                </div>
            <?php endif; ?>
        </div>
    </div>
    
    <!-- Seção 3: Histórico Geral de Atendimentos/Compras -->
    <div class="card shadow-lg">
        <div class="card-header bg-success text-white">
            <h5 class="mb-0"><i class="fas fa-shopping-cart me-2"></i> Histórico de Atendimentos e Compras (<?php echo count($historico_atendimentos); ?> últimos)</h5>
        </div>
        <div class="card-body p-0">
            <?php if (empty($historico_atendimentos)): ?>
                <div class="alert alert-info text-center m-3">
                    Não há histórico de atendimentos ou compras para este cliente.
                </div>
            <?php else: ?>
                <div class="table-responsive">
                    <table class="table table-striped table-hover mb-0">
                        <thead class="table-dark">
                            <tr>
                                <th scope="col">Data</th>
                                <th scope="col">Pet Envolvido</th>
                                <th scope="col">Tipo de Serviço/Produto</th>
                                <th scope="col">Valor Total</th>
                                <th scope="col">Observações</th>
                            </tr>
                        </thead>
                        <tbody>
                            <?php foreach ($historico_atendimentos as $item): ?>
                            <tr>
                                <td><?php echo date('d/m/Y', strtotime($item['data_atendimento'])); ?></td>
                                <td><?php echo htmlspecialchars($item['pet_nome'] ?? 'N/A (Compra/Serviço Geral)'); ?></td>
                                <td><?php echo htmlspecialchars($item['tipo_servico']); ?></td>
                                <td>R$ <?php echo number_format($item['valor_total'], 2, ',', '.'); ?></td>
                                <td><?php echo htmlspecialchars(substr($item['observacoes'] ?? '', 0, 50)) . (strlen($item['observacoes'] ?? '') > 50 ? '...' : ''); ?></td>
                            </tr>
                            <?php endforeach; ?>
                        </tbody>
                    </table>
                </div>
                <div class="card-footer text-muted text-center">
                    Exibindo os <?php echo count($historico_atendimentos); ?> atendimentos mais recentes.
                </div>
            <?php endif; ?>
        </div>
    </div>

</div>

<!-- OBS: Este arquivo exige que a tabela 'atendimento' exista no seu banco de dados, com as colunas: 
     id, cliente_id, pet_id (opcional), data_atendimento, tipo_servico, valor_total, observacoes. -->
