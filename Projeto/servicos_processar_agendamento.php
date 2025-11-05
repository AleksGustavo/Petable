<?php
// Arquivo: servicos_processar_agendamento.php
session_start();
require_once 'conexao.php'; 

// Verifica se a conexão está ativa
if (empty($conexao)) {
    // Redireciona com erro se a conexão falhar
    $_SESSION['erro'] = "Erro: Conexão com o banco de dados falhou.";
    header("Location: servicos_agendamentos_cadastro.php");
    exit;
}

// ---------------------------------------------------------
// 1. Coleta e Validação Básica dos Dados
// ---------------------------------------------------------

// Verifica se o método é POST
if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $_SESSION['erro'] = "Erro: Acesso inválido.";
    header("Location: servicos_agendamentos_cadastro.php");
    exit;
}

// Coleta e sanitiza dados
$cliente_id = filter_input(INPUT_POST, 'cliente_id', FILTER_VALIDATE_INT);
$pet_id = filter_input(INPUT_POST, 'pet_id', FILTER_VALIDATE_INT);
$data_agendamento = filter_input(INPUT_POST, 'data_agendamento');
$hora_agendamento = filter_input(INPUT_POST, 'hora_agendamento');
$observacoes = filter_input(INPUT_POST, 'observacoes', FILTER_SANITIZE_STRING);
$vacina_retorno_previsto = filter_input(INPUT_POST, 'vacina_retorno_previsto');

// Coleta o JSON de serviços agendados
$servicos_agendados_json = filter_input(INPUT_POST, 'servicos_agendados_json');
$servicos_agendados = $servicos_agendados_json ? json_decode($servicos_agendados_json, true) : [];

// Determina o funcionário (depende do tipo de serviço, mas é o mesmo campo de funcionário)
$funcionario_id = 0;
if (isset($_POST['funcionario_id_banhotosa']) && !empty($_POST['funcionario_id_banhotosa'])) {
    $funcionario_id = filter_input(INPUT_POST, 'funcionario_id_banhotosa', FILTER_VALIDATE_INT);
} elseif (isset($_POST['funcionario_id_vacina']) && !empty($_POST['funcionario_id_vacina'])) {
    $funcionario_id = filter_input(INPUT_POST, 'funcionario_id_vacina', FILTER_VALIDATE_INT);
} elseif (isset($_POST['funcionario_id_consulta']) && !empty($_POST['funcionario_id_consulta'])) {
    $funcionario_id = filter_input(INPUT_POST, 'funcionario_id_consulta', FILTER_VALIDATE_INT);
}

// Validação crítica
if (!$cliente_id || !$pet_id || !$data_agendamento || !$hora_agendamento || empty($servicos_agendados) || !$funcionario_id) {
    $_SESSION['erro'] = "Erro de validação: Dados essenciais faltando ou inválidos (Cliente, Pet, Data/Hora, Funcionário ou Serviços).";
    header("Location: servicos_agendamentos_cadastro.php");
    exit;
}

// Combina data e hora para o formato DATETIME do MySQL
$datahora_agendamento = $data_agendamento . ' ' . $hora_agendamento . ':00';

// Variáveis para a inserção principal
$status_padrao = 'agendado'; // Ajustado para o ENUM da sua tabela: 'agendado'
$usuario_cadastro_id = $_SESSION['usuario_id'] ?? 1; // ID do usuário logado (Ajuste isso para usar a $_SESSION real)
$total_estimado = 0.00; // Valor a ser inserido

$agendamento_id = null;


// ---------------------------------------------------------
// INÍCIO DA TRANSAÇÃO
// Garante o COMMIT ou o ROLLBACK de todas as inserções.
// ---------------------------------------------------------
mysqli_begin_transaction($conexao);

try {
    // ---------------------------------------------------------
    // 2. Inserção na Tabela 'agendamento'
    // A QUERY FOI RE-AJUSTADA para o novo schema SQL:
    // Campos: pet_id, funcionario_id, data_agendamento, status, observacoes, cliente_id, total_estimado, usuario_cadastro_id
    // ---------------------------------------------------------

    $sql_agendamento = "INSERT INTO agendamento (pet_id, funcionario_id, data_agendamento, status, observacoes, cliente_id, total_estimado, usuario_cadastro_id) 
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
    
    $stmt_agendamento = mysqli_prepare($conexao, $sql_agendamento);

    if (!$stmt_agendamento) {
        throw new Exception("Erro na preparação da inserção principal: " . mysqli_error($conexao));
    }

    // String de tipos: i i s s s i s i (8 placeholders)
    // pet_id (i), funcionario_id (i), data_agendamento (s), status (s), observacoes (s), cliente_id (i), total_estimado (s), usuario_cadastro_id (i)
    // O 'total_estimado' é ligado como string 's' por segurança de formato decimal.
    mysqli_stmt_bind_param($stmt_agendamento, "iississi", 
        $pet_id, 
        $funcionario_id, 
        $datahora_agendamento, 
        $status_padrao, 
        $observacoes, 
        $cliente_id, 
        $total_estimado, 
        $usuario_cadastro_id
    );

    if (!mysqli_stmt_execute($stmt_agendamento)) {
        throw new Exception("Erro ao executar inserção principal: " . mysqli_stmt_error($stmt_agendamento));
    }

    $agendamento_id = mysqli_insert_id($conexao);
    mysqli_stmt_close($stmt_agendamento);
    
    // ---------------------------------------------------------
    // 3. Inserção nas Tabelas de Detalhes
    // ---------------------------------------------------------
    
    $vacina_catalogo_id = 0; 
    $servicos_principais_ids = [];
    $has_vacina = false;

    // Separa os serviços principais e os detalhes de vacinação
    foreach ($servicos_agendados as $item) {
        if (is_numeric($item)) {
            $servicos_principais_ids[] = (int)$item;
        } elseif (is_array($item) && isset($item['vacina_catalogo_id'])) {
            // É o objeto de detalhe de vacinação (presume-se que o serviço de vacina também está em $servicos_principais_ids)
            $vacina_catalogo_id = (int)$item['vacina_catalogo_id'];
            $has_vacina = true;
        }
    }
    
    // Insere os serviços na tabela agendamento_servico
    $sql_detalhe = "INSERT INTO agendamento_servico (agendamento_id, servico_id, preco_estimado, data_execucao, status) VALUES (?, ?, ?, NULL, ?)";
    $stmt_detalhe = mysqli_prepare($conexao, $sql_detalhe);

    if (!$stmt_detalhe) {
         throw new Exception("Erro na preparação da inserção de serviço detalhe: " . mysqli_error($conexao));
    }

    foreach ($servicos_principais_ids as $servico_id) {
        $preco_estimado_detalhe = 0.00; // Valor real deve ser buscado ou enviado
        $status_servico = 'Agendado'; // Ajustado para o ENUM da tabela detalhe
        
        // i i s s (agendamento_id, servico_id, preco_estimado, status)
        mysqli_stmt_bind_param($stmt_detalhe, "iids", // Aqui o preco_estimado é ligado como 'd' (Double)
            $agendamento_id, 
            $servico_id, 
            $preco_estimado_detalhe, 
            $status_servico
        );
        
        if (!mysqli_stmt_execute($stmt_detalhe)) {
            throw new Exception("Erro ao inserir agendamento_servico (Serviço ID: {$servico_id}): " . mysqli_stmt_error($stmt_detalhe));
        }
    }
    mysqli_stmt_close($stmt_detalhe);
    
    // Se for vacina, insere na tabela agendamento_vacina
    if ($has_vacina && $vacina_catalogo_id && $vacina_retorno_previsto) {
         $sql_vacina = "INSERT INTO agendamento_vacina (agendamento_id, vacina_catalogo_id, veterinario_id, data_retorno_prevista) 
                        VALUES (?, ?, ?, ?)";
        $stmt_vacina = mysqli_prepare($conexao, $sql_vacina);
        
        if (!$stmt_vacina) {
             throw new Exception("Erro na preparação da inserção de vacina detalhe: " . mysqli_error($conexao));
        }
        
        // i i i s (agendamento_id, vacina_catalogo_id, veterinario_id, data_retorno_prevista)
        mysqli_stmt_bind_param($stmt_vacina, "iiis", 
            $agendamento_id, 
            $vacina_catalogo_id, 
            $funcionario_id, // Usamos o funcionário do agendamento como veterinario_id
            $vacina_retorno_previsto
        );
        
        if (!mysqli_stmt_execute($stmt_vacina)) {
            throw new Exception("Erro ao inserir agendamento_vacina: " . mysqli_stmt_error($stmt_vacina));
        }
        mysqli_stmt_close($stmt_vacina);
    }

    // ---------------------------------------------------------
    // FIM DA TRANSAÇÃO: COMMIT
    // ---------------------------------------------------------
    mysqli_commit($conexao);
    
    // Redirecionamento de Sucesso
    $_SESSION['sucesso'] = "Agendamento (ID: {$agendamento_id}) criado com sucesso!";
    header("Location: servicos_listar.php"); 
    
} catch (Exception $e) {
    // ---------------------------------------------------------
    // TRATAMENTO DE ERROS: ROLLBACK
    // ---------------------------------------------------------
    mysqli_rollback($conexao);
    
    // Log detalhado do erro
    error_log("Erro de Agendamento (ID: {$agendamento_id}): " . $e->getMessage() . 
              " | MySQLi Error: " . mysqli_error($conexao));
              
    // Mensagem de erro para o usuário
    $_SESSION['erro'] = "Falha ao processar o agendamento. Tente novamente. Detalhe: " . $e->getMessage();
    
    header("Location: servicos_agendamentos_cadastro.php");
    
} finally {
    mysqli_close($conexao);
    exit;
}
?>