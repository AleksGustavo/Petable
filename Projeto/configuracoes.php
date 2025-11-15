<?php
session_start();
// Inicia a sessão para garantir que as variáveis de sessão estejam acessíveis
require_once 'conexao.php'; // Inclui a conexão

// Verifica se o usuário está logado.
if (!isset($_SESSION['logado']) || $_SESSION['logado'] !== true || !isset($_SESSION['id_usuario'])) {
    // Se não estiver logado ou sem o ID na sessão, exibe erro
    echo '<div class="alert alert-danger">Erro: Usuário não logado ou sessão incompleta. Por favor, faça login novamente.</div>';
    exit();
}

// CHAVES CORRIGIDAS: Acessando 'id_usuario' e 'usuario'
$usuario_id = $_SESSION['id_usuario'];
$usuario_logado = htmlspecialchars($_SESSION['usuario']);

$usuario_detalhes = [];
$funcionario_detalhes = []; // NOVO: Para guardar os dados do funcionário
$mensagem_conexao = '';

// 1. Busca Detalhes da Conta (Tabela: usuario)
if (isset($conexao)) {
    
    $sql_detalhes = "SELECT * FROM usuario WHERE id = ?";
    
    // É necessário usar Prepared Statements para segurança
    if ($stmt = mysqli_prepare($conexao, $sql_detalhes)) {
        mysqli_stmt_bind_param($stmt, "i", $usuario_id);
        mysqli_stmt_execute($stmt);
        $result = mysqli_stmt_get_result($stmt);
        
        if ($result && $row = mysqli_fetch_assoc($result)) {
            $usuario_detalhes = $row;
        } else {
            $mensagem_conexao = '<div class="alert alert-warning">Detalhes do usuário (conta) não encontrados no banco.</div>';
        }
        mysqli_stmt_close($stmt);
    } else {
          $mensagem_conexao = '<div class="alert alert-danger">Erro ao preparar a consulta do perfil (usuario): ' . mysqli_error($conexao) . '</div>';
    }

    // 2. NOVO BLOCO: Busca Detalhes do Funcionário (Tabela: funcionario)
    // A coluna 'data_nascimento' DEVE existir na tabela funcionario
    $sql_funcionario = "SELECT nome, data_nascimento FROM funcionario WHERE usuario_id = ?";
    
    if ($stmt_func = mysqli_prepare($conexao, $sql_funcionario)) {
        mysqli_stmt_bind_param($stmt_func, "i", $usuario_id);
        mysqli_stmt_execute($stmt_func);
        $result_func = mysqli_stmt_get_result($stmt_func);
        
        if ($result_func && $row_func = mysqli_fetch_assoc($result_func)) {
            $funcionario_detalhes = $row_func;
        }
        // Nota: Se não encontrar, mantemos $funcionario_detalhes vazio e usamos 'Não informado' como fallback.
        mysqli_stmt_close($stmt_func);
    } else {
        $mensagem_conexao = '<div class="alert alert-danger">Erro ao preparar a consulta do perfil (funcionário): ' . mysqli_error($conexao) . '</div>';
    }

} else {
    $mensagem_conexao = '<div class="alert alert-danger">Erro Crítico: Conexão com o banco de dados falhou (conexao.php).</div>';
}


// --- 3. Consolidação e Formatação de Informações para Exibição ---

// Informações Estáticas do Pet Shop
$nome_pet_shop = "Pet & Pet Shop";
$email_contato = "pet&pet@exemplo.com";
$telefone_contato = "(19) 98765-4321";
$endereco_base = "Rua Francisco Travestino, 320 - Quaglia - Leme/SP";

// Dados da tabela 'usuario'
$email_usuario = $usuario_detalhes['email'] ?? 'Não informado'; 
$data_cadastro = $usuario_detalhes['data_cadastro'] ?? 'Não informado'; 
$papel_usuario = (isset($usuario_detalhes['papel_id']) && $usuario_detalhes['papel_id'] == 1) ? 'Administrador' : 'Funcionário';

// Dados da tabela 'funcionario'
$nome_completo_func = $funcionario_detalhes['nome'] ?? 'Não informado';
$data_nascimento = $funcionario_detalhes['data_nascimento'] ?? 'Não informado';

// Quebra o nome completo do funcionário em nome e sobrenome (heurística do arquivo de formulário)
$partes_nome = explode(' ', trim($nome_completo_func));
$nome_func = htmlspecialchars(array_shift($partes_nome) ?: 'Não informado');
$sobrenome_func = htmlspecialchars(implode(' ', $partes_nome) ?: 'Não informado');

// Formatação da Data de Nascimento (para o formato brasileiro dd/mm/yyyy)
$data_nascimento_formatada = 'Não informado';
if (!empty($data_nascimento) && $data_nascimento !== 'Não informado') {
    try {
        $data_nascimento_obj = new DateTime($data_nascimento);
        $data_nascimento_formatada = $data_nascimento_obj->format('d/m/Y');
    } catch (Exception $e) {
        $data_nascimento_formatada = 'Data Inválida';
    }
}
?>

<div class="container mt-4">
    
    <h1 class="display-5 mb-4 text-primary">
        <i class="fas fa-user-cog me-2"></i> Configurações do Perfil
    </h1>
    
    <div id="status-message-area" class="mb-4">
        <?= $mensagem_conexao ?>
        </div>

    <div class="card shadow-lg mb-5 border-dark">
        <div class="card-header bg-dark text-white d-flex justify-content-between align-items-center">
            <h5 class="mb-0"><i class="fas fa-id-card-alt me-2"></i> Detalhes da sua Conta</h5>
            <span class="badge bg-light text-dark fs-6">
                <?= $usuario_logado ?>
            </span>
        </div>
        <div class="card-body">
            
            <div class="row">
                <div class="col-md-6">
                    <p class="mb-2"><strong>Nome de Usuário (Login):</strong> <code><?= $usuario_logado ?></code></p>
                    <p class="mb-2"><strong>Primeiro Nome:</strong> <?= $nome_func ?></p>
                    <p class="mb-2"><strong>Sobrenome(s):</strong> <?= $sobrenome_func ?></p>
                    <p class="mb-2"><strong>Data de Nascimento:</strong> <?= $data_nascimento_formatada ?></p>
                    <p class="mb-2"><strong>Email de Contato:</strong> <?= htmlspecialchars($email_usuario) ?></p>
                    <p class="mb-2"><strong>Nível de Acesso:</strong> <span class="badge bg-primary"><?= $papel_usuario ?></span></p>
                    <p class="mb-2 text-muted small"><strong>Membro Desde:</strong> <?= $data_cadastro ?></p>
                </div>
                
                <div class="col-md-6 text-md-end pt-3 pt-md-0">
                    <p class="mb-3 text-muted">Ações de segurança da conta:</p>
                    <button class="btn btn-warning mb-2 btn-mudar-senha" data-id="<?= $usuario_id ?>">
                        <i class="fas fa-key me-1"></i> Alterar Senha
                    </button>
                    <button class="btn btn-info mb-2 btn-editar-dados" data-id="<?= $usuario_id ?>">
                        <i class="fas fa-user-edit me-1"></i> Editar Dados Pessoais
                    </button>
                </div>
            </div>
            
            <div id="perfil-form-area" class="mt-4"></div>
        </div>
    </div>
    <div class="card shadow-sm mb-4 border-secondary">
        <div class="card-header bg-secondary text-white">
            <h5 class="mb-0"><i class="fas fa-store me-2"></i> Informações do Pet & Pet</h5>
        </div>
        <div class="card-body">
            <p><strong>E-mail de Contato:</strong> <a href="mailto:<?= $email_contato ?>"><?= $email_contato ?></a></p>
            <p><strong>Telefone:</strong> <?= $telefone_contato ?></p>
            <p><strong>Endereço Base:</strong> <?= $endereco_base ?></p>
        </div>
    </div>
    
</div>

<?php
if (isset($conexao)) {
    @mysqli_close($conexao); 
}
?>

<script>
    // Lógica para carregar formulário de Alteração de Senha/Dados
    $(document).ready(function() {
        
        // Carrega o formulário de alteração de senha/dados
        $(document).on('click', '.btn-mudar-senha, .btn-editar-dados', function() {
            var idUsuario = $(this).data('id');
            var tipoAcao = $(this).hasClass('btn-mudar-senha') ? 'senha' : 'dados';
            var arquivo = 'perfil_form_' + tipoAcao + '.php';
            
            $('#perfil-form-area').html(
                '<div class="alert alert-warning mt-3">' +
                '<i class="fas fa-hourglass-half me-1"></i> Carregando formulário de ' + 
                (tipoAcao === 'senha' ? 'Alteração de Senha' : 'Edição de Dados Pessoais') + '...' +
                '</div>'
            );

            // A chamada AJAX real para carregar o formulário
            $.ajax({
                url: arquivo, 
                type: 'GET',
                data: { user_id: idUsuario },
                success: function(data) {
                    $('#perfil-form-area').html(data);
                    if (typeof inicializarMascarasEValidacoes === 'function') {
                          inicializarMascarasEValidacoes(); 
                    }
                },
                error: function(xhr, status, error) {
                    $('#perfil-form-area').html('<div class="alert alert-danger mt-3">Erro ao carregar o formulário. Status: ' + xhr.status + '</div>');
                }
            });
        });
        
    });
</script>