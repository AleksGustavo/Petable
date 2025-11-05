<?php
// Arquivo: servicos_listar.php - Lista os Serviços
require_once 'conexao.php'; 

// Inicializa a lista de serviços
$servicos = [];
$erro_sql = '';

// SQL para buscar todos os serviços
$sql_servicos = "SELECT id, nome, descricao, preco, duracao_media, ativo 
                 FROM servico
                 ORDER BY nome ASC";

$result_servicos = mysqli_query($conexao, $sql_servicos);

if ($result_servicos) {
    // Busca todos os resultados em um array associativo
    $servicos = mysqli_fetch_all($result_servicos, MYSQLI_ASSOC);
    mysqli_free_result($result_servicos);
} else {
    // Mensagem de erro para depuração
    $erro_sql = "Erro ao buscar serviços: " . mysqli_error($conexao);
}

mysqli_close($conexao);

// Função auxiliar para formatar preço (R$ 1.234,56)
function formatarMoeda($valor) {
    return 'R$ ' . number_format($valor, 2, ',', '.');
}
?>

<div class="d-flex justify-content-between align-items-center mb-4">
    <h2><i class="fas fa-cut me-2"></i> Gerenciamento de Serviços</h2>
    <a href="#" class="btn btn-success item-menu-ajax" data-pagina="servicos_cadastro.php">
        <i class="fas fa-plus me-2"></i> Novo Serviço
    </a>
</div>

<div id="status-message-area">
    <?php if (!empty($erro_sql)): ?>
        <div class="alert alert-danger" role="alert">
            <strong>Erro no Banco de Dados:</strong> <?php echo $erro_sql; ?>
            <p class="mt-2">Não foi possível carregar a lista de serviços.</p>
        </div>
    <?php endif; ?>
</div>

<?php if (empty($servicos)): ?>
    <div class="alert alert-info" role="alert">
        Nenhum serviço cadastrado no momento.
    </div>
<?php else: ?>
    <div class="table-responsive">
        <table class="table table-striped table-hover shadow-sm">
            <thead class="table-dark">
                <tr>
                    <th>ID</th>
                    <th>Nome do Serviço</th>
                    <th>Preço</th>
                    <th>Duração Média (min)</th>
                    <th>Descrição</th>
                    <th>Status</th>
                    <th>Ações</th>
                </tr>
            </thead>
            <tbody>
                <?php foreach ($servicos as $servico): 
                    $status_badge = $servico['ativo'] == 1 ? 'bg-success' : 'bg-danger';
                    $status_text = $servico['ativo'] == 1 ? 'Ativo' : 'Inativo';
                ?>
                    <tr>
                        <td><?php echo htmlspecialchars($servico['id']); ?></td>
                        <td><?php echo htmlspecialchars($servico['nome']); ?></td>
                        <td><?php echo formatarMoeda($servico['preco']); ?></td>
                        <td><?php echo htmlspecialchars($servico['duracao_media'] ?? 'N/A'); ?></td>
                        <td><?php echo htmlspecialchars(substr($servico['descricao'] ?? '', 0, 50)) . (strlen($servico['descricao'] ?? '') > 50 ? '...' : ''); ?></td>
                        <td><span class="badge <?php echo $status_badge; ?>"><?php echo $status_text; ?></span></td>
                        <td>
                            <a href="#" 
                               class="btn btn-sm btn-warning item-menu-ajax" 
                               data-pagina="servicos_cadastro.php" 
                               data-id="<?php echo $servico['id']; ?>" 
                               title="Editar Serviço">
                                <i class="fas fa-edit"></i>
                            </a>
                            
                            <button class="btn btn-sm <?php echo $servico['ativo'] == 1 ? 'btn-danger' : 'btn-primary'; ?>" 
                                    onclick="alterarStatusServico(<?php echo $servico['id']; ?>, <?php echo $servico['ativo']; ?>)" 
                                    title="<?php echo $servico['ativo'] == 1 ? 'Desativar Serviço' : 'Ativar Serviço'; ?>">
                                <i class="fas <?php echo $servico['ativo'] == 1 ? 'fa-trash' : 'fa-check-circle'; ?>"></i>
                            </button>
                        </td>
                    </tr>
                <?php endforeach; ?>
            </tbody>
        </table>
    </div>
<?php endif; ?>

<script>
    function alterarStatusServico(servicoId, statusAtual) {
        if (confirm('Tem certeza que deseja ' + (statusAtual == 1 ? 'desativar' : 'ativar') + ' este serviço?')) {
            // Em uma aplicação real, você faria uma requisição AJAX para 'servicos_alterar_status.php'
            console.log("Solicitação de alteração de status para o Serviço ID: " + servicoId);
            // location.reload(); // Recarrega para simular
        }
    }
</script>