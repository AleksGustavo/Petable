<?php
// Arquivo: perfil_form_dados.php
// CORRIGIDO PARA BUSCAR TODOS OS DADOS NECESSÁRIOS NA TABELA 'funcionario'

session_start();
require_once 'conexao.php'; // Inclui a conexão

// 1. Verificação e Obtenção do ID do Usuário (Sessão)
$usuario_id = $_SESSION['id_usuario'] ?? ($_GET['user_id'] ?? null);

$funcionario_detalhes = [];
$mensagem_erro = '';

if (!$usuario_id) {
    $mensagem_erro = '<div class="alert alert-danger mt-3">Erro: ID do usuário não fornecido. Por favor, faça login novamente.</div>';
} elseif (isset($conexao)) {
    
    // CORREÇÃO: Busca os campos 'nome', 'data_nascimento' e 'sexo'
    $sql_detalhes = "SELECT nome, data_nascimento, sexo FROM funcionario WHERE usuario_id = ?";
    
    // Linha 21 (original) corrigida:
    if ($stmt = mysqli_prepare($conexao, $sql_detalhes)) {
        mysqli_stmt_bind_param($stmt, "i", $usuario_id);
        mysqli_stmt_execute($stmt);
        $result = mysqli_stmt_get_result($stmt);
        
        if ($result && $row = mysqli_fetch_assoc($result)) {
            // Preenche com os dados atuais
            $funcionario_detalhes = $row;
        } else {
            $mensagem_erro = '<div class="alert alert-warning mt-3">Detalhes do funcionário não encontrados no banco. (Verifique se há um registro na tabela funcionario para este usuário_id: ' . $usuario_id . ')</div>';
        }
        mysqli_stmt_close($stmt);
    } else {
        $mensagem_erro = '<div class="alert alert-danger mt-3">Erro ao preparar a consulta: ' . mysqli_error($conexao) . '</div>';
    }
} else {
    $mensagem_erro = '<div class="alert alert-danger mt-3">Erro Crítico: Conexão com o banco de dados falhou.</div>';
}

// Valores padrão ou atuais
$nome_completo_atual = $funcionario_detalhes['nome'] ?? '';
// Agora busca os valores do banco, se existirem
$data_nascimento_atual = $funcionario_detalhes['data_nascimento'] ?? ''; 
$sexo_atual = $funcionario_detalhes['sexo'] ?? ''; 

// Tenta quebrar o nome completo (coluna 'nome') em primeiro nome e sobrenome (heurística)
$partes_nome = explode(' ', trim($nome_completo_atual));
$nome_atual = htmlspecialchars(array_shift($partes_nome) ?: ''); // O primeiro nome
$sobrenome_atual = htmlspecialchars(implode(' ', $partes_nome)); // O restante

// Array de opções para o campo sexo
$opcoes_sexo = [
    '' => 'Selecione...',
    'M' => 'Masculino',
    'F' => 'Feminino',
    'Outro' => 'Outro'
];

// Se houver erro, apenas exibe a mensagem de erro
if ($mensagem_erro) {
    echo $mensagem_erro;
    // Opcional: fechar conexão se estiver aberta
    if (isset($conexao)) { @mysqli_close($conexao); }
    exit();
}
?>

<div class="card p-0 shadow-lg mt-3 main-compact-card border-info">
    <div class="card-header bg-info text-white">
        <h5 class="mb-0"><i class="fas fa-user-edit me-2"></i> Editar Seus Dados Pessoais (Funcionário)</h5>
    </div>
    <div class="card-body">
        
        <div id="edit-status-message-area" class="mb-3">
            <!-- Mensagens de sucesso ou erro do processamento (via AJAX) aparecerão aqui -->
        </div>

        <form id="form-edicao-perfil" method="POST" action="perfil_processar_dados.php">
            <!-- Campo oculto para garantir o ID de usuário (FK) no processamento -->
            <input type="hidden" name="id_usuario" value="<?= $usuario_id ?>">

            <div class="row g-2 g-compact">
                
                <!-- Nome -->
                <div class="col-md-6">
                    <label for="nome" class="form-label">Primeiro Nome *</label>
                    <input type="text" id="nome" name="nome" class="form-control form-control-sm input-letters-only" 
                           value="<?= $nome_atual ?>" required>
                    <small class="text-muted">Será unido com o Sobrenome para formar o Nome Completo.</small>
                </div>
                
                <!-- Sobrenome -->
                <div class="col-md-6">
                    <label for="sobrenome" class="form-label">Sobrenome(s) *</label>
                    <input type="text" id="sobrenome" name="sobrenome" class="form-control form-control-sm input-letters-only" 
                           value="<?= $sobrenome_atual ?>" required>
                    <small class="text-muted">O restante do seu nome.</small>
                </div>

                <hr class="mt-4 mb-2">
                <h5 class="mb-2"><i class="fas fa-calendar-alt me-1"></i> Informações Adicionais</h5>

                <!-- Data de Nascimento (Agora existe no banco) -->
                <div class="col-md-4">
                    <label for="data_nascimento" class="form-label">Data de Nascimento</label>
                    <input type="date" id="data_nascimento" name="data_nascimento" class="form-control form-control-sm" 
                           value="<?= $data_nascimento_atual ?>">
                </div>
                
                <!-- Sexo (Agora existe no banco) -->
                <div class="col-md-4">
                    <label for="sexo" class="form-label">Sexo</label>
                    <select id="sexo" name="sexo" class="form-select form-select-sm">
                        <?php foreach ($opcoes_sexo as $value => $label): ?>
                            <option value="<?= $value ?>" <?= $value == $sexo_atual ? 'selected' : '' ?>>
                                <?= $label ?>
                            </option>
                        <?php endforeach; ?>
                    </select>
                </div>

                <!-- Botões de Ação -->
                <div class="col-12 mt-4 text-end">
                    <button type="button" class="btn btn-secondary btn-sm me-2" onclick="$('#perfil-form-area').html('');">
                         <i class="fas fa-times-circle me-2"></i> Cancelar
                    </button>
                    <button type="submit" class="btn btn-info btn-sm" id="btn-salvar-dados">
                        <i class="fas fa-save me-2"></i> Salvar Alterações
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>

<?php
if (isset($conexao)) {
    @mysqli_close($conexao); 
}
?>

<script>
    // Lógica AJAX para submeter o formulário sem recarregar a página
    $(document).ready(function() {
        $('#form-edicao-perfil').on('submit', function(e) {
            e.preventDefault();
            
            var form = $(this);
            var btn = $('#btn-salvar-dados');
            var statusArea = $('#edit-status-message-area');
            
            // Desabilita o botão e mostra carregamento
            btn.prop('disabled', true).html('<i class="fas fa-sync fa-spin me-2"></i> Salvando...');
            statusArea.html('<div class="alert alert-info">Processando a atualização...</div>');
            
            $.ajax({
                url: form.attr('action'),
                type: form.attr('method'),
                data: form.serialize(),
                dataType: 'json', 
                success: function(response) {
                    if (response.sucesso) {
                        statusArea.html('<div class="alert alert-success"><i class="fas fa-check-circle me-2"></i> ' + response.mensagem + '</div>');
                        // Recarrega a página inteira para atualizar a sessão/detalhes
                        setTimeout(function() {
                            window.location.reload(); 
                        }, 1500); 
                    } else {
                        statusArea.html('<div class="alert alert-danger"><i class="fas fa-exclamation-circle me-2"></i> ' + response.mensagem + '</div>');
                    }
                },
                error: function() {
                    statusArea.html('<div class="alert alert-danger">Erro de comunicação com o servidor ao tentar salvar os dados.</div>');
                },
                complete: function() {
                    btn.prop('disabled', false).html('<i class="fas fa-save me-2"></i> Salvar Alterações');
                }
            });
        });
    });
</script>