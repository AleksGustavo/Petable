<?php
// Arquivo: pets_cadastro.php
// Objetivo: Formulário para cadastrar um novo Pet, pré-selecionando o dono (cliente).

require_once 'conexao.php'; 

$cliente_id_preselecionado = $_GET['cliente_id'] ?? null;
$nome_cliente = 'Cliente Não Encontrado';
$especies = [];
$racas = [];

// ==================================================================================
// CONFIGURAÇÃO DE CAMINHOS WEB: AJUSTADO PARA O SEU PATH
// ==================================================================================
$BASE_PATH = '/PHP_PI/'; 
$URL_UPLOADS = $BASE_PATH . 'uploads/fotos_pets/'; 
$URL_PLACEHOLDER = $BASE_PATH . 'assets/img/pet_placeholder.png'; 
// ==================================================================================

try {
    
    if ($cliente_id_preselecionado) {
        // CORREÇÃO: Removido 'sobrenome' da seleção. A coluna 'nome' agora é usada sozinha.
        $stmt_cliente = $pdo->prepare("SELECT nome FROM cliente WHERE id = ? AND ativo = 1");
        $stmt_cliente->execute([$cliente_id_preselecionado]);
        $cliente = $stmt_cliente->fetch(PDO::FETCH_ASSOC);
        
        if ($cliente) {
            // CORREÇÃO: Usando apenas $cliente['nome']
            $nome_cliente = htmlspecialchars($cliente['nome']);
        } else {
            $cliente_id_preselecionado = null; 
        }
    }

    // Busca de espécies
    $stmt_especies = $pdo->query("SELECT id, nome FROM especie ORDER BY nome ASC");
    $especies = $stmt_especies->fetchAll(PDO::FETCH_ASSOC);

    // Busca de raças
    $stmt_racas = $pdo->query("SELECT id, nome FROM raca ORDER BY nome ASC");
    $racas = $stmt_racas->fetchAll(PDO::FETCH_ASSOC);

} catch (PDOException $e) {
    error_log("Erro ao carregar dados essenciais para cadastro de pet: " . $e->getMessage());
    $nome_cliente = 'ERRO DE DB';
    // Adicione esta linha temporariamente para ver o erro se ele persistir:
    // echo '<div class="alert alert-danger">Erro de Banco de Dados: ' . htmlspecialchars($e->getMessage()) . '</div>';
    $especies = [];
    $racas = [];
}
?>

<div class="d-flex justify-content-between align-items-center mb-4">
    <h2><i class="fas fa-paw me-2"></i> Cadastro de Novo Pet</h2>
    <a href="#" class="btn btn-secondary item-menu-ajax" data-pagina="clientes_listar.php">
        <i class="fas fa-arrow-left me-2"></i> Voltar para Busca
    </a>
</div>

<div id="status-message-area"></div>

<?php if (!$cliente_id_preselecionado): ?>
    <div class="alert alert-danger">
        **Erro:** Cliente não selecionado ou inválido. Volte para a busca e clique no botão "Add Pet" do cliente correto.
    </div>
<?php else: ?>

<div class="card shadow-sm mb-4 bg-light">
    <div class="card-body">
        <h5 class="mb-0">Dono do Pet: <span class="text-primary"><?= $nome_cliente ?></span></h5>
        <small class="text-muted">As informações abaixo serão salvas para este cliente.</small>
    </div>
</div>

<div class="card shadow-sm">
    <div class="card-body">
        <form id="form-cadastro-pet" action="pets_processar.php" method="POST" enctype="multipart/form-data">
            
            <input type="hidden" name="acao" value="cadastrar">
            <input type="hidden" name="cliente_id" value="<?= htmlspecialchars($cliente_id_preselecionado) ?>">

            
            <div class="row">
                <div class="col-md-12 mb-3">
                    <label for="nome" class="form-label">Nome do Pet <span class="text-danger">*</span></label>
                    <input type="text" id="nome" name="nome" class="form-control" placeholder="Nome do Pet" required>
                </div>
            </div>
            
            <div class="row">
                <div class="col-md-4 mb-3">
                    <label for="especie_id" class="form-label">Espécie <span class="text-danger">*</span></label>
                    <select id="especie_id" name="especie_id" class="form-select" required> 
                        <option value="">Selecione a Espécie...</option>
                        <?php foreach ($especies as $e): ?>
                            <option value="<?= $e['id'] ?>"><?= htmlspecialchars($e['nome']) ?></option>
                        <?php endforeach; ?>
                    </select>
                </div>

                <div class="col-md-4 mb-3">
                    <label for="raca_id" class="form-label">Raça</label>
                    <select id="raca_id" name="raca_id" class="form-select">
                        <option value="">Selecione a Raça (Opcional)</option>
                        <?php foreach ($racas as $r): ?>
                            <option value="<?= $r['id'] ?>"><?= htmlspecialchars($r['nome']) ?></option>
                        <?php endforeach; ?>
                    </select>
                </div>
                
                <div class="col-md-4 mb-3">
                    <label for="data_nascimento" class="form-label">Data de Nascimento</label>
                    <input type="date" id="data_nascimento" name="data_nascimento" class="form-control">
                </div>
            </div>
            
            <div class="row" id="pet-porte-row" style="display:none;">
                <div class="col-md-4 mb-3">
                    <label for="porte" class="form-label">Porte (Apenas para Cães) <span class="text-danger">*</span></label>
                    <select id="porte" name="porte" class="form-select" disabled required>
                        <option value="">Selecione o Porte</option>
                        <option value="Pequeno">Pequeno</option>
                        <option value="Medio">Médio</option>
                        <option value="Grande">Grande</option>
                    </select>
                    <div class="form-text">Obrigatório apenas para a espécie "Cachorro".</div>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4 mb-3">
                    <label for="peso" class="form-label">Peso (Kg)</label>
                    <input type="number" step="0.01" id="peso" name="peso" class="form-control" placeholder="Ex: 5.25">
                </div>
                
                <div class="col-md-8 mb-3 pt-4 d-flex align-items-start">
                    <div class="form-check form-switch me-4">
                        <input class="form-check-input" type="checkbox" id="castrado" name="castrado" value="1">
                        <label class="form-check-label" for="castrado">Castrado</label>
                    </div>
                    <div class="form-check form-switch">
                        <input class="form-check-input" type="checkbox" id="vacinado" name="vacinado" value="1">
                        <label class="form-check-label" for="vacinado">Vacinado</label>
                    </div>
                </div>
            </div>

            <div class="mb-3">
                <label for="foto" class="form-label">Foto do Pet</label>
                <input class="form-control" type="file" id="foto" name="foto" accept="image/*">
                <div class="form-text">Envie uma foto do pet (opcional).</div>
            </div>

            <button type="submit" class="btn btn-primary mt-3">
                <i class="fas fa-save me-1"></i> Cadastrar Pet
            </button>
            
        </form>
    </div>
</div>

<script>
$(document).ready(function() {
    // Lógica para controle de visibilidade do campo 'Porte'
    $('#especie_id').on('change', function() {
        // ASSUNÇÃO: O ID da Espécie "Cachorro" é 1.
        const especieId = $(this).val();
        const porteRow = $('#pet-porte-row');
        const porteSelect = $('#porte');

        // Verifica se a espécie selecionada é Cachorro (ID 1)
        if (especieId === '1') { 
            // Se for Cachorro, torna o campo visível e obrigatório.
            porteRow.show();
            porteSelect.prop('disabled', false);
            porteSelect.prop('required', true); 
        } else {
            // Para outras espécies (Gato, etc.), esconde e desabilita.
            porteRow.hide();
            porteSelect.val(''); // Limpa a seleção
            porteSelect.prop('disabled', true);
            porteSelect.prop('required', false);
        }
    }).trigger('change'); // Dispara uma vez na inicialização, caso o campo já venha preenchido (embora aqui não seja o caso)

    // Lógica de processamento de formulário AJAX (se houver, deve ser mantida aqui)

});
</script>


<?php endif; ?>