<?php
require_once 'conexao.php';

$pet_id = filter_input(INPUT_GET, 'id', FILTER_VALIDATE_INT);
$pet = null;

$URL_UPLOADS = 'uploads/fotos_pets/'; 
$URL_PLACEHOLDER = 'assets/img/pet_placeholder.png'; 

if (empty($conexao)) {
    echo '<div class="alert alert-danger">Erro crítico: Conexão mysqli indisponível.</div>';
    exit();
}

if ($pet_id) {
    try {
        $sql_pet = "SELECT 
                        p.id, p.nome, p.data_nascimento, p.foto AS foto_path, p.castrado,
                        r.nome AS raca_nome, 
                        e.nome AS especie_nome, e.id AS especie_id,
                        c.id AS cliente_id, c.nome AS cliente_nome, c.telefone AS cliente_telefone
                    FROM 
                        pet p
                    JOIN 
                        cliente c ON p.cliente_id = c.id
                    LEFT JOIN 
                        raca r ON p.raca_id = r.id
                    LEFT JOIN 
                        especie e ON p.especie_id = e.id
                    WHERE 
                        p.id = ?";
                        
        $stmt_pet = mysqli_prepare($conexao, $sql_pet);
        mysqli_stmt_bind_param($stmt_pet, "i", $pet_id);
        mysqli_stmt_execute($stmt_pet);
        $result_pet = mysqli_stmt_get_result($stmt_pet);
        $pet = mysqli_fetch_assoc($result_pet);
        mysqli_stmt_close($stmt_pet);

        $sql_vacina = "SELECT COUNT(id) AS total_vacinas FROM carteira_vacina WHERE pet_id = ?";
        $stmt_vacina = mysqli_prepare($conexao, $sql_vacina);
        mysqli_stmt_bind_param($stmt_vacina, "i", $pet_id);
        mysqli_stmt_execute($stmt_vacina);
        $result_vacina = mysqli_stmt_get_result($stmt_vacina);
        $dados_vacina = mysqli_fetch_assoc($result_vacina);
        mysqli_stmt_close($stmt_vacina);
        
        $total_vacinas = $dados_vacina['total_vacinas'];
        
    } catch (Exception $e) {
        error_log("Erro ao carregar detalhes do Pet: " . $e->getMessage());
        echo '<div class="alert alert-danger">Erro crítico ao carregar a ficha do Pet. Tente novamente.</div>';
        $pet = null;
    }
}

if (!$pet) {
    echo '<div class="alert alert-danger">Pet não encontrado ou ID inválido.</div>';
    exit();
}

$is_castrado = (bool)$pet['castrado'];
$status_castracao = $is_castrado ? 'Castrado' : 'Não Castrado';
$castracao_class = $is_castrado ? 'success' : 'warning text-dark';
$castracao_icon = $is_castrado ? 'fas fa-shield-alt' : 'fas fa-stethoscope';

$is_vacinado = $total_vacinas > 0;
$status_vacina = $is_vacinado ? 'Histórico Ativo' : 'Sem Histórico';
$vacina_class = $is_vacinado ? 'primary' : 'secondary';
$vacina_icon = $is_vacinado ? 'fas fa-file-medical' : 'fas fa-syringe';

$foto_path = $pet['foto_path'] ?? '';
$foto_url = (!empty($foto_path) && file_exists($URL_UPLOADS . $foto_path)) 
             ? $URL_UPLOADS . $foto_path
             : $URL_PLACEHOLDER;

function get_pet_icon($especie_nome) {
    $nome = mb_strtolower($especie_nome ?? '');
    if (strpos($nome, 'cão') !== false || strpos($nome, 'cachorro') !== false) return '<i class="fas fa-dog"></i>';
    if (strpos($nome, 'gato') !== false) return '<i class="fas fa-cat"></i>';
    if (strpos($nome, 'ave') !== false) return '<i class="fas fa-dove"></i>';
    return '<i class="fas fa-paw"></i>';
}

$iconPet = get_pet_icon($pet['especie_nome']);

$data_nascimento = new DateTime($pet['data_nascimento'] ?? 'now');
$hoje = new DateTime('now');
$idade = $data_nascimento->diff($hoje);
$idade_texto = '';

if ($idade->y > 0) {
    $idade_texto = $idade->y . ' ano(s)';
} elseif ($idade->m > 0) {
    $idade_texto = $idade->m . ' mes(es)';
} else {
    $idade_texto = $idade->d . ' dia(s)';
}
?>

<div class="container mt-4">
    
    <div class="d-flex justify-content-between align-items-center mb-4 pb-2 border-bottom">
        <h2 class="fw-bold text-dark">
            <span class="text-primary me-2"><?php echo $iconPet; ?></span> Ficha Detalhada: <?php echo htmlspecialchars($pet['nome']); ?>
        </h2>
        
        <div class="btn-group" role="group" aria-label="Ações do Pet">
            <a href="#" class="btn btn-outline-primary item-menu-ajax" 
                data-pagina="pets_editar.php?id=<?php echo $pet_id; ?>" 
                title="Editar as informações do Pet">
                <i class="fas fa-edit me-1"></i> Editar
            </a>
            
            <a href="#" class="btn btn-outline-warning item-menu-ajax" 
                data-pagina="pets_vacinas.php?pet_id=<?php echo $pet_id; ?>" 
                title="Ver Histórico de Vacinas">
                <i class="fas fa-syringe me-1"></i> Carteira
            </a>

            <a href="#" class="btn btn-outline-danger btn-excluir-pet" 
                data-id="<?php echo $pet_id; ?>" 
                data-url="pets_processar.php?acao=excluir"
                title="Excluir o Pet permanentemente (Ação Imediata)">
                <i class="fas fa-trash-alt"></i>
            </a>
            
        </div>
    </div>
    
    <div class="row">
        
        <div class="col-lg-4 mb-4">
            
            <div class="card shadow-lg border-0 text-center">
                <div class="card-header bg-light p-4">
                    <img src="<?php echo $foto_url; ?>" class="img-fluid rounded-circle border border-5 border-white shadow-sm" 
                         style="width: 200px; height: 200px; object-fit: cover;" 
                         alt="Foto do Pet">
                </div>
                <div class="card-body">
                    <h4 class="fw-bold mb-1 text-primary"><?php echo htmlspecialchars($pet['nome']); ?></h4>
                    <p class="text-muted small mb-3">#ID: <?php echo $pet['id']; ?></p>
                    
                    <div class="d-grid gap-2">
                        <span class="btn btn-sm btn-<?php echo $castracao_class; ?> text-uppercase fw-bold">
                            <i class="<?php echo $castracao_icon; ?> me-2"></i> <?php echo $status_castracao; ?>
                        </span>
                        <a href="#" class="btn btn-sm btn-outline-<?php echo $vacina_class; ?> item-menu-ajax fw-bold"
                           data-pagina="pets_vacinas.php?pet_id=<?php echo $pet_id; ?>">
                            <i class="<?php echo $vacina_icon; ?> me-2"></i> <?php echo $total_vacinas; ?> Registros de Vacina
                        </a>
                    </div>
                </div>
            </div>
            
        </div>
        
        <div class="col-lg-8">
            
            <div class="card shadow-sm mb-4">
                <div class="card-header bg-primary text-white fw-bold">
                    <i class="fas fa-info-circle me-2"></i> Dados Biométricos
                </div>
                <div class="card-body">
                    <div class="row g-3">
                        <div class="col-md-4">
                            <p class="text-muted small text-uppercase mb-0">Espécie</p>
                            <h5 class="fw-bold text-dark"><?php echo htmlspecialchars($pet['especie_nome'] ?? 'N/A'); ?></h5>
                        </div>
                        <div class="col-md-4">
                            <p class="text-muted small text-uppercase mb-0">Raça</p>
                            <h5 class="fw-bold text-dark"><?php echo htmlspecialchars($pet['raca_nome'] ?? 'Não informada'); ?></h5>
                        </div>
                        <div class="col-md-4">
                            <p class="text-muted small text-uppercase mb-0">Nascimento / Idade</p>
                            <h5 class="fw-bold text-dark">
                                <?php echo date('d/m/Y', strtotime($pet['data_nascimento'])); ?>
                                <span class="badge bg-secondary ms-2"><?php echo $idade_texto; ?></span>
                            </h5>
                        </div>
                        </div>
                </div>
            </div>
            
            <div class="card shadow-sm mb-4">
                <div class="card-header bg-info text-white fw-bold">
                    <i class="fas fa-user-alt me-2"></i> Detalhes do Dono
                </div>
                <div class="card-body">
                    <div class="row">
                        <div class="col-md-6">
                            <p class="text-muted small text-uppercase mb-0">Nome</p>
                            <h5 class="fw-bold">
                                <a href="#" class="text-decoration-none text-dark item-menu-ajax" data-pagina="clientes_detalhes.php?id=<?php echo $pet['cliente_id']; ?>">
                                    <?php echo htmlspecialchars($pet['cliente_nome']); ?> <i class="fas fa-external-link-alt small ms-1"></i>
                                </a>
                            </h5>
                        </div>
                        <div class="col-md-6">
                            <p class="text-muted small text-uppercase mb-0">Telefone</p>
                            <h5 class="fw-bold"><?php echo htmlspecialchars($pet['cliente_telefone']); ?></h5>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="card shadow-sm">
                <div class="card-header bg-secondary text-white fw-bold">
                    <i class="fas fa-clipboard-list me-2"></i> Histórico / Observações
                </div>
                <div class="card-body text-muted">
                    <p>Adicione aqui um campo de observações gerais do pet, últimas consultas ou alergias importantes.</p>
                </div>
            </div>
            
        </div>
        
    </div>
</div>

<script>
$(document).ready(function() {
    if (typeof carregarConteudo === 'undefined') {
        window.carregarConteudo = function(paginaUrl) {
            $('#conteudo-principal').html('<div class="text-center p-5"><i class="fas fa-spinner fa-spin fa-2x"></i> Carregando...</div>');
            $('#conteudo-principal').load(paginaUrl, function(response, status, xhr) {
                if (status == "error") {
                    var msg = "Erro ao carregar página: ";
                    $('#conteudo-principal').html('<div class="alert alert-danger">' + msg + xhr.status + " " + xhr.statusText + '</div>');
                }
            });
        };
    }

    $('body').off('click', '.item-menu-ajax').on('click', '.item-menu-ajax', function(e) {
        e.preventDefault();
        var pagina = $(this).data('pagina');
        if (pagina) {
            carregarConteudo(pagina);
        }
    });

    $('body').off('click', '.btn-excluir-pet').on('click', '.btn-excluir-pet', function(e) {
        e.preventDefault();

        const petId = $(this).data('id');
        const deleteUrl = $(this).data('url');

        if (!petId) {
             alert('❌ Erro: ID do Pet não encontrado no botão. Não é possível excluir.');
             return; 
        }

        const $btn = $(this);
        const htmlOriginal = $btn.html();
        $btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> Excluindo...');

        $.ajax({
            url: deleteUrl, 
            method: 'POST', 
            data: { id: petId },
            dataType: 'json', 
            success: function(response) {
                if (response.success) {
                    alert('✅ Sucesso: ' + response.message);
                    carregarConteudo('clientes_listar.php'); 
                } else {
                    alert('❌ Erro ao excluir: ' + (response.message || 'Erro desconhecido.'));
                    $btn.prop('disabled', false).html(htmlOriginal);
                }
            },
            error: function(xhr, status, error) {
                alert('❌ Erro de comunicação com o servidor. Status: ' + status + ' (' + error + ')');
                $btn.prop('disabled', false).html(htmlOriginal);
            }
        });
    });
});
</script>