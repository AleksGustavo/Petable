<?php
// Arquivo: pets_detalhes.php - Ficha Detalhada do Pet
require_once 'conexao.php'; // Garante a conexão com o banco de dados ($conexao - mysqli)

$pet_id = filter_input(INPUT_GET, 'id', FILTER_VALIDATE_INT);
$pet = null;

// ==================================================================================
// CAMINHOS WEB: AJUSTE ESTES CAMINHOS PARA ONDE ESTÃO SUAS PASTAS
// ==================================================================================
$URL_UPLOADS = 'uploads/fotos_pets/'; 
$URL_PLACEHOLDER = 'assets/img/pet_placeholder.png'; // Caminho para uma imagem padrão
// ==================================================================================

// Verifica se a conexão mysqli está ativa.
if (empty($conexao)) {
    echo '<div class="alert alert-danger">Erro crítico: Conexão mysqli indisponível.</div>';
    exit();
}

if ($pet_id) {
    try {
        // 1. Busca os detalhes do Pet (INCLUINDO JOIN COM RACAS PARA PEGAR O NOME) e do Dono
        $sql_pet = "SELECT 
                        p.id, p.nome, p.data_nascimento, p.foto AS foto_path, p.castrado,
                        r.nome AS raca_nome, 
                        c.id AS cliente_id, c.nome AS cliente_nome, c.telefone AS cliente_telefone
                    FROM 
                        pet p
                    JOIN 
                        cliente c ON p.cliente_id = c.id
                    LEFT JOIN 
                        raca r ON p.raca_id = r.id
                    WHERE 
                        p.id = ?";
                        
        // Usa prepared statement mysqli
        $stmt_pet = mysqli_prepare($conexao, $sql_pet);
        mysqli_stmt_bind_param($stmt_pet, "i", $pet_id);
        mysqli_stmt_execute($stmt_pet);
        $result_pet = mysqli_stmt_get_result($stmt_pet);
        $pet = mysqli_fetch_assoc($result_pet);
        mysqli_stmt_close($stmt_pet);

        // 2. Verifica se o Pet tem vacinas na carteira
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

// Configurações de Status
$is_castrado = (bool)$pet['castrado'];
$status_castracao = $is_castrado ? 'Castrado' : 'Não Castrado';
$castracao_class = $is_castrado ? 'badge bg-success' : 'badge bg-warning text-dark';

$is_vacinado = $total_vacinas > 0;
$status_vacina = $is_vacinado ? 'Com Histórico' : 'Sem Histórico';
$vacina_class = $is_vacinado ? 'badge bg-primary' : 'badge bg-secondary';

// Determina o caminho da foto
$foto_path = $pet['foto_path'] ?? '';
$foto_url = (!empty($foto_path) && file_exists($URL_UPLOADS . $foto_path)) 
             ? $URL_UPLOADS . $foto_path
             : $URL_PLACEHOLDER;
?>

<div class="container mt-4">
    <div class="d-flex justify-content-between align-items-center mb-4">
        <h2><i class="fas fa-paw me-2"></i> Ficha do Pet: <?php echo htmlspecialchars($pet['nome']); ?></h2>
        
        <div class="btn-group" role="group" aria-label="Ações do Pet">
            
            <a href="#" class="btn btn-danger item-menu-ajax" 
                data-pagina="pets_processar.php?acao=excluir&id=<?php echo $pet_id; ?>" 
                data-confirmacao="Tem certeza que deseja EXCLUIR este pet e todos os seus registros (vacinas, agendamentos, etc.)? Esta ação é irreversível." 
                title="Excluir o Pet permanentemente">
                <i class="fas fa-trash-alt me-1"></i> Excluir Pet
            </a>
            
            <a href="#" class="btn btn-primary item-menu-ajax" 
                data-pagina="pets_editar.php?id=<?php echo $pet_id; ?>" 
                title="Editar as informações do Pet">
                <i class="fas fa-edit me-1"></i> Atualizar Pet
            </a>
            
            <a href="#" class="btn btn-warning item-menu-ajax" 
                data-pagina="pets_carteira_vacinas.php?pet_id=<?php echo $pet_id; ?>" 
                title="Ver Histórico de Vacinas">
                <i class="fas fa-file-invoice me-1"></i> Ver Carteira de Vacinas
            </a>
        </div>
    </div>

    <div class="card shadow-lg mb-4">
        <div class="card-body">
            <div class="row">
                <div class="col-md-3 text-center">
                    <img src="<?php echo $foto_url; ?>" class="img-fluid rounded-circle border border-4 border-light shadow-sm mb-3" style="width: 180px; height: 180px; object-fit: cover;" alt="Foto do Pet">
                </div>
                
                <div class="col-md-9">
                    <h5 class="mb-3 text-secondary">Informações do Pet</h5>
                    <ul class="list-group list-group-flush mb-4">
                        <li class="list-group-item"><strong>Nome:</strong> <?php echo htmlspecialchars($pet['nome']); ?></li>
                        <li class="list-group-item"><strong>Raça:</strong> <?php echo htmlspecialchars($pet['raca_nome'] ?? 'Não informada'); ?></li>
                        <li class="list-group-item"><strong>Nascimento:</strong> <?php echo date('d/m/Y', strtotime($pet['data_nascimento'])); ?></li>
                    </ul>
                    
                    <h5 class="mb-3 text-secondary">Status</h5>
                    <div class="d-flex flex-wrap gap-2">
                        <div>
                            <span class="fw-bold me-2">Castrado:</span>
                            <span class="<?php echo $castracao_class; ?> p-2"><?php echo $status_castracao; ?></span>
                        </div>
                        <div>
                            <span class="fw-bold me-2">Vacinação:</span>
                            <span class="<?php echo $vacina_class; ?> p-2"><?php echo $status_vacina; ?></span>
                        </div>
                    </div>
                    
                    <h5 class="mt-4 mb-3 text-secondary">Detalhes do Dono</h5>
                    <p class="mb-1">
                        <strong>Nome:</strong> 
                        <a href="#" class="item-menu-ajax" data-pagina="clientes_detalhes.php?id=<?php echo $pet['cliente_id']; ?>">
                            <?php echo htmlspecialchars($pet['cliente_nome']); ?>
                        </a>
                    </p>
                    <p><strong>Telefone:</strong> <?php echo htmlspecialchars($pet['cliente_telefone']); ?></p>
                </div>
            </div>
        </div>
    </div>
</div>

<script>
// ESTE BLOCO DE CÓDIGO É NECESSÁRIO PARA GARANTIR QUE OS BOTÕES DENTRO DESTE CONTEÚDO 
// CARREGADO VIA AJAX FUNCIONEM CORRETAMENTE.

$(document).ready(function() {
    // 1. Replicar a função de carregamento AJAX, se ela não estiver no escopo global
    if (typeof carregarConteudo === 'undefined') {
        window.carregarConteudo = function(paginaUrl) {
            // Esta é uma SIMULAÇÃO de como seu sistema principal deve carregar o conteúdo.
            // VOCÊ PODE PRECISAR AJUSTAR O SELETOR DA DIV PRINCIPAL (ex: '#conteudo-principal')
            $('#conteudo-principal').html('<div class="text-center p-5"><i class="fas fa-spinner fa-spin fa-2x"></i> Carregando...</div>');
            $('#conteudo-principal').load(paginaUrl, function(response, status, xhr) {
                if (status == "error") {
                    var msg = "Erro ao carregar página: ";
                    $('#conteudo-principal').html('<div class="alert alert-danger">' + msg + xhr.status + " " + xhr.statusText + '</div>');
                }
            });
        };
    }

    // 2. Aplicar a DELEGAÇÃO DE EVENTOS para os links AJAX
    // Isso garante que mesmo os elementos carregados dinamicamente (como esta página) 
    // tenham a funcionalidade de clique AJAX.
    $('body').off('click', '.item-menu-ajax').on('click', '.item-menu-ajax', function(e) {
        e.preventDefault();
        var pagina = $(this).data('pagina');
        var confirmacao = $(this).data('confirmacao'); // Pega a mensagem de confirmação

        if (confirmacao) {
            // Se houver mensagem de confirmação (para o botão Excluir)
            if (confirm(confirmacao)) {
                carregarConteudo(pagina);
            }
        } else {
            // Para botões normais (Atualizar, Ver Carteira)
            if (pagina) {
                carregarConteudo(pagina);
            }
        }
    });

    // Chama o evento de clique uma vez para garantir que o script esteja no escopo
    // (Mesmo que o código acima já use delegação de evento, é uma boa prática
    // garantir que o DOM esteja pronto para o script ser executado.)
});
</script>