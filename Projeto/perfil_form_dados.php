<?php
session_start();
require_once 'conexao.php'; 

if (!isset($_SESSION['logado']) || !isset($_SESSION['id_usuario']) || !isset($_GET['user_id'])) {
    echo '<div class="alert alert-danger">Erro de acesso: Usuário ou ID não identificados.</div>';
    exit();
}

$usuario_id = intval($_GET['user_id']);

$sql = "SELECT nome, data_nascimento, cpf, telefone, cep, rua, bairro, numero, complemento 
        FROM funcionario 
        WHERE usuario_id = ?";

$funcionario = [];

if ($stmt = mysqli_prepare($conexao, $sql)) {
    
    mysqli_stmt_bind_param($stmt, "i", $usuario_id);
    mysqli_stmt_execute($stmt);
    
    $result = mysqli_stmt_get_result($stmt); 
    
    if ($result && $row = mysqli_fetch_assoc($result)) {
        $funcionario = $row;
    } else {
        echo '<div class="alert alert-warning">Aviso: Detalhes do funcionário não encontrados.</div>';
        mysqli_stmt_close($stmt);
        exit();
    }
    
    mysqli_stmt_close($stmt);
} else {
    echo '<div class="alert alert-danger">Erro ao preparar a consulta: ' . mysqli_error($conexao) . '</div>';
    exit();
}

$nome_completo = $funcionario['nome'] ?? '';
$partes_nome = explode(' ', trim($nome_completo), 2);
$primeiro_nome = $partes_nome[0] ?? '';
$sobrenome = $partes_nome[1] ?? '';
?>

<div class="card mt-4 border-info">
    <div class="card-header bg-info text-white">
        <h5 class="mb-0"><i class="fas fa-edit me-2"></i> Edição de Dados Pessoais</h5>
    </div>
    <div class="card-body">
        
        <div id="edit-profile-status-message"></div>

        <form id="formEditarDadosPessoais" action="editar_perfil_processar.php" method="POST">
            
            <input type="hidden" name="usuario_id" value="<?= $usuario_id ?>">

            <div class="row mb-3">
                <div class="col-md-6">
                    <label for="nome">Primeiro Nome</label>
                    <input type="text" name="primeiro_nome" id="nome" class="form-control" 
                           value="<?= htmlspecialchars($primeiro_nome) ?>" required>
                </div>
                <div class="col-md-6">
                    <label for="sobrenome">Sobrenome(s)</label>
                    <input type="text" name="sobrenome" id="sobrenome" class="form-control" 
                           value="<?= htmlspecialchars($sobrenome) ?>" required>
                </div>
            </div>

            <div class="row mb-3">
                <div class="col-md-6">
                    <label for="data_nascimento">Data de Nascimento</label>
                    <input type="date" name="data_nascimento" id="data_nascimento" class="form-control" 
                           value="<?= htmlspecialchars($funcionario['data_nascimento'] ?? '') ?>" required>
                </div>
                <div class="col-md-6">
                    <label for="telefone">Telefone</label>
                    <input type="text" name="telefone" id="telefone" class="form-control telefone-mask" 
                           value="<?= htmlspecialchars($funcionario['telefone'] ?? '') ?>">
                </div>
            </div>

            <div class="row mb-3">
                <div class="col-md-6">
                    <label for="cpf">CPF</label>
                    <input type="text" id="cpf" class="form-control cpf-mask" 
                           value="<?= htmlspecialchars($funcionario['cpf'] ?? '') ?>" disabled title="O CPF não pode ser alterado após o cadastro inicial.">
                </div>
            </div>

            <div class="row mb-3">
                <div class="col-md-4">
                    <label for="cep">CEP</label>
                    <input type="text" name="cep" id="cep" class="form-control cep-mask" 
                           value="<?= htmlspecialchars($funcionario['cep'] ?? '') ?>">
                </div>
                <div class="col-md-8">
                    <label for="rua">Rua/Avenida</label>
                    <input type="text" name="rua" id="rua" class="form-control" 
                           value="<?= htmlspecialchars($funcionario['rua'] ?? '') ?>">
                </div>
            </div>

            <div class="row mb-3">
                <div class="col-md-5">
                    <label for="bairro">Bairro</label>
                    <input type="text" name="bairro" id="bairro" class="form-control" 
                           value="<?= htmlspecialchars($funcionario['bairro'] ?? '') ?>">
                </div>
                <div class="col-md-3">
                    <label for="numero">Número</label>
                    <input type="text" name="numero" id="numero" class="form-control" 
                           value="<?= htmlspecialchars($funcionario['numero'] ?? '') ?>">
                </div>
                <div class="col-md-4">
                    <label for="complemento">Complemento</label>
                    <input type="text" name="complemento" id="complemento" class="form-control" 
                           value="<?= htmlspecialchars($funcionario['complemento'] ?? '') ?>">
                </div>
            </div>
            
            <button type="submit" class="btn btn-success mt-3 w-100">
                <i class="fas fa-save me-2"></i> Salvar Alterações
            </button>
        </form>
    </div>
</div>

<script>
$(document).ready(function() {
    if (typeof inicializarMascarasEValidacoes === 'function') {
        inicializarMascarasEValidacoes(); 
    }

    $('#formEditarDadosPessoais').on('submit', function(e) {
        e.preventDefault();
        
        let form = $(this);
        let statusArea = $('#edit-profile-status-message');
        
        statusArea.html('<div class="alert alert-info">Processando...</div>');

        $.ajax({
            url: form.attr('action'),
            type: 'POST',
            data: form.serialize(),
            dataType: 'json',
            success: function(response) {
                if(response.sucesso) {
                    statusArea.html('<div class="alert alert-success">' + response.mensagem + '</div>');
                    setTimeout(function() {
                        window.location.reload(); 
                    }, 1000);
                } else {
                    statusArea.html('<div class="alert alert-danger">' + response.mensagem + '</div>');
                }
            },
            error: function() {
                statusArea.html('<div class="alert alert-danger">Erro na comunicação com o servidor. Tente novamente.</div>');
            }
        });
    });
});
</script>