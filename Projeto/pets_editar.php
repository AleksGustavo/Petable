<?php
// Arquivo: pets_editar.php
// Objetivo: Formulário para editar as informações de um pet existente.

require_once 'conexao.php'; 

$pet_id = $_GET['id'] ?? null;
$pet = null;
$especies = [];
$racas = [];
$nome_cliente = 'Cliente Não Encontrado';

// ==================================================================================
// CONFIGURAÇÃO DE CAMINHOS WEB: AJUSTADO PARA GARANTIR CAMINHO RELATIVO NO FRONT-END
// ==================================================================================
// REMOVIDO: $BASE_PATH = 'PHP_PI'; 
// Acessa o diretório de uploads a partir da raiz do projeto ou caminho relativo
$URL_UPLOADS = 'uploads/fotos_pets/'; 
// Se o seu servidor exige o prefixo, use: $URL_UPLOADS = '/PHP_PI/uploads/fotos_pets/'; 
// Mas para o PHP encontrar o arquivo no disco, geralmente um caminho relativo basta.
// ==================================================================================

if (!$pet_id) {
    echo '<div class="alert alert-danger">ID do Pet não fornecido para edição.</div>';
    exit();
}

try {
    // 1. Carrega os dados do Pet - AGORA COM P.PORTE DEFINITIVO NA QUERY
    $stmt_pet = $pdo->prepare("
        SELECT 
            p.id, p.cliente_id, p.nome, p.data_nascimento, p.peso, p.castrado, p.vacinado, 
            p.especie_id, p.raca_id, p.foto, p.porte, 
            c.nome AS nome_cliente 
        FROM pet p
        JOIN cliente c ON p.cliente_id = c.id
        WHERE p.id = ?
    ");
    $stmt_pet->execute([$pet_id]);
    $pet = $stmt_pet->fetch(PDO::FETCH_ASSOC);

    if (!$pet) {
        echo '<div class="alert alert-danger">Pet não encontrado.</div>';
        exit();
    }
    
    $nome_cliente = htmlspecialchars($pet['nome_cliente']);
    // Garante que o porte seja lido corretamente ou seja string vazia
    $porte_atual = $pet['porte'] ?? ''; 

    // 2. Busca lista de espécies e raças
    $stmt_especies = $pdo->query("SELECT id, nome FROM especie ORDER BY nome ASC");
    $especies = $stmt_especies->fetchAll(PDO::FETCH_ASSOC);

    $stmt_racas = $pdo->query("SELECT id, nome FROM raca ORDER BY nome ASC");
    $racas = $stmt_racas->fetchAll(PDO::FETCH_ASSOC);

} catch (PDOException $e) {
    error_log("Erro ao carregar dados do pet para edição: " . $e->getMessage());
    echo '<div class="alert alert-danger">Erro ao carregar dados do pet. Por favor, verifique a conexão e a estrutura do banco de dados.</div>';
    exit();
}
?>

<div class="d-flex justify-content-between align-items-center mb-4">
    <h2><i class="fas fa-edit me-2"></i> Editar Pet: <?= htmlspecialchars($pet['nome']) ?></h2>
    <a href="#" class="btn btn-secondary item-menu-ajax" data-pagina="clientes_listar.php">
        <i class="fas fa-arrow-left me-2"></i> Voltar para Busca
    </a>
</div>

<div id="status-message-area"></div>

<div class="card shadow-sm mb-4 bg-light">
    <div class="card-body">
        <h5 class="mb-0">Dono do Pet: <span class="text-primary"><?= $nome_cliente ?></span></h5>
    </div>
</div>

<div class="card shadow-sm">
    <div class="card-body">
        <form id="form-cadastro-pet" action="pets_processar.php" method="POST" enctype="multipart/form-data">
            
            <input type="hidden" name="acao" value="editar">
            <input type="hidden" name="id" value="<?= htmlspecialchars($pet['id']) ?>">
            <input type="hidden" name="cliente_id" value="<?= htmlspecialchars($pet['cliente_id']) ?>">
            <input type="hidden" name="foto_antiga" value="<?= htmlspecialchars($pet['foto'] ?? '') ?>"> 
            
            <div class="row">
                <div class="col-md-12 mb-3">
                    <label for="nome" class="form-label">Nome do Pet <span class="text-danger">*</span></label>
                    <input type="text" id="nome" name="nome" class="form-control" value="<?= htmlspecialchars($pet['nome']) ?>" required>
                </div>
            </div>
            
            <div class="row">
                <div class="col-md-4 mb-3">
                    <label for="especie_id" class="form-label">Espécie <span class="text-danger">*</span></label>
                    <select id="especie_id" name="especie_id" class="form-select" required> <option value="">Selecione a Espécie...</option>
                        <?php foreach ($especies as $e): ?>
                            <option value="<?= $e['id'] ?>" <?= ($e['id'] == $pet['especie_id']) ? 'selected' : '' ?>>
                                <?= htmlspecialchars($e['nome']) ?>
                            </option>
                        <?php endforeach; ?>
                    </select>
                </div>

                <div class="col-md-4 mb-3">
                    <label for="raca_id" class="form-label">Raça</label>
                    <select id="raca_id" name="raca_id" class="form-select">
                        <option value="">Selecione a Raça (Opcional)</option>
                        <?php foreach ($racas as $r): ?>
                            <option value="<?= $r['id'] ?>" <?= ($r['id'] == $pet['raca_id']) ? 'selected' : '' ?>>
                                <?= htmlspecialchars($r['nome']) ?>
                            </option>
                        <?php endforeach; ?>
                    </select>
                </div>
                
                <div class="col-md-4 mb-3">
                    <label for="data_nascimento" class="form-label">Data de Nascimento</label>
                    <input type="date" id="data_nascimento" name="data_nascimento" class="form-control" value="<?= htmlspecialchars($pet['data_nascimento']) ?>">
                </div>
            </div>

            <div class="row" id="pet-porte-row" style="display:none;">
                <div class="col-md-4 mb-3">
                    <label for="porte" class="form-label">Porte (Apenas para Cães)</label>
                    <select id="porte" name="porte" class="form-select" disabled>
                        <option value="">Selecione o Porte</option>
                        <option value="Pequeno" <?= ($porte_atual == 'Pequeno') ? 'selected' : '' ?>>Pequeno</option>
                        <option value="Medio" <?= ($porte_atual == 'Medio') ? 'selected' : '' ?>>Médio</option>
                        <option value="Grande" <?= ($porte_atual == 'Grande') ? 'selected' : '' ?>>Grande</option>
                    </select>
                    <div class="form-text">Preencha se for Cachorro.</div>
                </div>
            </div>
            <div class="row">
                <div class="col-md-4 mb-3">
                    <label for="peso" class="form-label">Peso (Kg)</label>
                    <input type="number" step="0.01" id="peso" name="peso" class="form-control" value="<?= htmlspecialchars($pet['peso']) ?>" placeholder="Ex: 5.25">
                </div>
                
                <div class="col-md-8 mb-3 pt-4 d-flex align-items-start">
                    <div class="form-check form-switch me-4">
                        <input class="form-check-input" type="checkbox" id="castrado" name="castrado" value="1" <?= $pet['castrado'] ? 'checked' : '' ?>>
                        <label class="form-check-label" for="castrado">Castrado</label>
                    </div>
                    <div class="form-check form-switch">
                        <input class="form-check-input" type="checkbox" id="vacinado" name="vacinado" value="1" <?= $pet['vacinado'] ? 'checked' : '' ?>>
                        <label class="form-check-label" for="vacinado">Vacinado</label>
                    </div>
                </div>
            </div>

            <div class="mb-3">
                <label for="foto" class="form-label">Foto do Pet</label>
                <input class="form-control" type="file" id="foto" name="foto" accept="image/*">
                
                <?php if (!empty($pet['foto'])): ?>
                    <div class="mt-2 d-flex align-items-center">
                        <img src="<?= $URL_UPLOADS . htmlspecialchars($pet['foto']) ?>" alt="Foto atual do pet" style="max-width: 100px; height: auto; border: 1px solid #ccc; border-radius: 5px;" class="me-3">
                        <div class="form-check form-check-inline">
                            <input class="form-check-input" type="checkbox" id="remover_foto" name="remover_foto" value="1">
                            <label class="form-check-label" for="remover_foto">Remover foto atual</label>
                        </div>
                    </div>
                <?php else: ?>
                    <p class="text-muted mt-2">Nenhuma foto cadastrada para este pet.</p>
                <?php endif; ?>
                <div class="form-text">Envie uma nova foto ou marque para remover a atual.</div>
            </div>

            <button type="submit" class="btn btn-primary mt-3">
                <i class="fas fa-save me-1"></i> Salvar Alterações
            </button>
            
            <button type="button" class="btn btn-danger mt-3" onclick="history.back()">
                <i class="fas fa-times me-1"></i> Cancelar
            </button>
            
        </form>
    </div>
</div>

<script>
$(document).ready(function() {
    // Lógica para controle de visibilidade e obrigatoriedade do campo 'Porte'
    function togglePorteField() {
        // ASSUNÇÃO: O ID da Espécie "Cachorro" é 1. (Mantenha este ID consistente com seu BD!)
        const especieId = $('#especie_id').val();
        const porteRow = $('#pet-porte-row');
        const porteSelect = $('#porte');

        // Note: Se o ID da sua espécie "Cachorro" for outro (ex: '2'), mude o '1' abaixo.
        if (especieId === '1') { 
            // Se for Cachorro, torna o campo visível e habilitado.
            porteRow.show();
            porteSelect.prop('disabled', false);
        } else {
            // Para outras espécies, esconde e desabilita.
            porteRow.hide();
            porteSelect.prop('disabled', true);
        }
    }

    // Aplica a lógica quando a espécie muda
    $('#especie_id').on('change', togglePorteField); 
    
    // Aplica a lógica na inicialização para exibir o estado correto do pet atual
    togglePorteField(); 
});
</script>