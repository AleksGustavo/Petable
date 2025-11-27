<?php
// Arquivo: clientes_editar.php

// 1. INCLUIR CONEXÃO
require_once 'conexao.php';

// 2. OBTER O ID DO CLIENTE
$cliente_id = filter_input(INPUT_GET, 'id', FILTER_VALIDATE_INT); 
$cliente_db_data = null;
$cliente = [];

if ($cliente_id && isset($conexao)) {
    // 3. BUSCAR DADOS DO CLIENTE NO BANCO DE DADOS
    try {
        $sql = "SELECT id, nome, cpf, telefone, data_nascimento, cep, rua, numero, bairro, complemento
                FROM cliente
                WHERE id = ?";
        
        $stmt = mysqli_prepare($conexao, $sql);
        mysqli_stmt_bind_param($stmt, "i", $cliente_id);
        mysqli_stmt_execute($stmt);
        $result = mysqli_stmt_get_result($stmt);
        $cliente_db_data = mysqli_fetch_assoc($result);
        mysqli_stmt_close($stmt);

        // Se o cliente foi encontrado, processa os dados
        if ($cliente_db_data) {
            
            // Lógica para dividir o 'nome' (real) em 'nome' e 'sobrenome'
            $nome_partes = explode(' ', $cliente_db_data['nome'], 2);
            $nome = $nome_partes[0] ?? '';
            $sobrenome = $nome_partes[1] ?? '';

            // Monta o array $cliente com a estrutura esperada pelo formulário
            $cliente = array_merge($cliente_db_data, [
                'nome' => $nome, 
                'sobrenome' => $sobrenome, 
                'celular' => $cliente_db_data['telefone'],
                'sexo' => 'N/A' 
            ]);
        }
    } catch (Exception $e) {
        error_log("Erro ao carregar cliente para edição: " . $e->getMessage());
    }
}

// Verifica se o cliente foi carregado.
if (!$cliente_db_data) {
    echo '<div class="alert alert-danger">Erro: Cliente não encontrado ou ID inválido. ID tentado: ' . htmlspecialchars($cliente_id) . '</div>';
    if (isset($conexao)) {
        mysqli_close($conexao);
    }
    exit(); 
}

// Fecha a conexão após buscar os dados
if (isset($conexao)) {
    mysqli_close($conexao);
}
?>

<div class="d-flex justify-content-between align-items-center mb-3">
    <h2><i class="fas fa-user-edit me-2"></i> Atualizar Cadastro do Cliente ID: <?php echo htmlspecialchars($cliente['id']); ?></h2>
    
    <div>
        <a href="#" class="btn btn-secondary item-menu-ajax btn-sm" data-pagina="clientes_listar.php">
            <i class="fas fa-list me-2"></i> Voltar à Lista
        </a>
    </div>
</div>

<div id="status-message-area">
</div>

<div class="card p-0 shadow-sm main-compact-card">
    <div class="card-body">
        <form id="form-atualizacao-cliente" method="POST" action="clientes_processar_atualizacao.php">
            
            <input type="hidden" name="cliente_id" value="<?php echo htmlspecialchars($cliente['id'] ?? ''); ?>">
            
            <div class="row g-2 g-compact">
                
                <div class="col-md-4">
                    <label for="nome" class="form-label">Nome *</label>
                    <input type="text" id="nome" name="nome" class="form-control form-control-sm input-letters-only" required value="<?php echo htmlspecialchars($cliente['nome'] ?? ''); ?>">
                </div>
                
                <div class="col-md-4">
                    <label for="sobrenome" class="form-label">Sobrenome *</label>
                    <input type="text" id="sobrenome" name="sobrenome" class="form-control form-control-sm input-letters-only" required value="<?php echo htmlspecialchars($cliente['sobrenome'] ?? ''); ?>">
                </div>
                
                <div class="col-md-4">
                    <label for="cpf" class="form-label">CPF *</label>
                    <input type="text" id="cpf" name="cpf" class="form-control form-control-sm mask-cpf input-numbers-only" required maxlength="14" placeholder="000.000.000-00" value="<?php echo htmlspecialchars($cliente['cpf'] ?? ''); ?>">
                </div>
                
                <div class="col-md-4">
                    <label for="celular" class="form-label">Celular *</label>
                    <input type="text" id="celular" name="celular" class="form-control form-control-sm mask-celular input-numbers-only" required maxlength="15" placeholder="(00) 00000-0000" value="<?php echo htmlspecialchars($cliente['celular'] ?? ''); ?>">
                </div>

                <hr class="mt-2">
                <h5 class="mb-2">Endereço</h5>

                <div class="col-md-3">
                    <label for="cep" class="form-label">CEP *</label>
                    <input type="text" id="cep" name="cep" class="form-control form-control-sm mask-cep input-numbers-only" required maxlength="9" placeholder="00000-000" value="<?php echo htmlspecialchars($cliente['cep'] ?? ''); ?>">
                </div>
                
                <div class="col-md-6">
                    <label for="rua" class="form-label">Rua *</label>
                    <input type="text" id="rua" name="rua" class="form-control form-control-sm" required value="<?php echo htmlspecialchars($cliente['rua'] ?? ''); ?>">
                </div>
                
                <div class="col-md-3">
                    <label for="numero" class="form-label">Número *</label>
                    <input type="text" id="numero" name="numero" class="form-control form-control-sm input-numbers-only" required value="<?php echo htmlspecialchars($cliente['numero'] ?? ''); ?>">
                </div>
                
                <div class="col-md-6">
                    <label for="bairro" class="form-label">Bairro</label>
                    <input type="text" id="bairro" name="bairro" class="form-control form-control-sm input-letters-only" value="<?php echo htmlspecialchars($cliente['bairro'] ?? ''); ?>">
                </div>
                
                <div class="col-md-6">
                    <label for="complemento" class="form-label">Complemento</label>
                    <input type="text" id="complemento" name="complemento" class="form-control form-control-sm" value="<?php echo htmlspecialchars($cliente['complemento'] ?? ''); ?>">
                </div>

                <hr class="mt-2">
                <h5 class="mb-2">Outros Dados</h5>

                <div class="col-md-4">
                    <label for="data_nascimento" class="form-label">Data de Nascimento</label>
                    <input type="date" id="data_nascimento" name="data_nascimento" class="form-control form-control-sm" value="<?php echo htmlspecialchars($cliente['data_nascimento'] ?? ''); ?>">
                </div>
                <div class="col-md-4">
                    <label for="sexo" class="form-label">Sexo</label>
                    <select id="sexo" name="sexo" class="form-select form-select-sm">
                        <option value="">Selecione...</option>
                        <option value="M" <?php echo (($cliente['sexo'] ?? '') === 'M') ? 'selected' : ''; ?>>Masculino</option>
                        <option value="F" <?php echo (($cliente['sexo'] ?? '') === 'F') ? 'selected' : ''; ?>>Feminino</option>
                        <option value="Outro" <?php echo (($cliente['sexo'] ?? '') === 'Outro') ? 'selected' : ''; ?>>Outro</option>
                    </select>
                </div>
                
                <div class="col-12 mt-3">
                    <button type="submit" class="btn btn-primary btn-sm">
                        <i class="fas fa-sync-alt me-2"></i> Salvar Alterações
                    </button>
                </div>
            </div>
        </form>
        
    </div>
</div>

<script src="https://cdn.jsdelivr.net/npm/sweetalert2@11"></script>

<script>
    $(document).ready(function() {
        const $form = $('#form-atualizacao-cliente'); 

        $form.on('submit', function(e) {
            
            e.preventDefault(); 
            
            const $submitButton = $(this).find('button[type="submit"]');
            $submitButton.prop('disabled', true).html('<i class="fas fa-spinner fa-spin me-2"></i> Processando...');

            $.ajax({
                url: 'clientes_processar_atualizacao.php',
                type: 'POST',
                data: $form.serialize(), 
                dataType: 'json',
                
                success: function(response) {
                    
                    let iconType = (response.status === 'success') ? 'success' : 
                                   (response.status === 'info') ? 'info' : 'error';
                    
                    let titleText = (response.status === 'success') ? 'Sucesso!' : (response.status === 'info') ? 'Atenção!' : 'Erro!';

                    Swal.fire({
                        icon: iconType, 
                        title: titleText,
                        text: response.message, 
                        timer: 3000, 
                        showConfirmButton: false,
                        toast: true,
                        position: 'top-end'
                    });
                },
                
                error: function(jqXHR, textStatus, errorThrown) {
                    Swal.fire({
                        icon: 'error',
                        title: 'Erro de Comunicação',
                        text: 'Não foi possível completar a ação. Verifique a resposta do servidor no console (F12).',
                    });
                },
                
                complete: function() {
                    // Restaura o botão
                    $submitButton.prop('disabled', false).html('<i class="fas fa-sync-alt me-2"></i> Salvar Alterações');
                }
            });
        });
    });
</script>