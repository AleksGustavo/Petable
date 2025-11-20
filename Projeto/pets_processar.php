<?php
// Arquivo: pets_processar.php
// Objetivo: Receber e processar o formulário de cadastro/edição de Pet, incluindo o upload de foto.

header('Content-Type: application/json');

require_once 'conexao.php'; // Usa PDO

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

// Diretório onde as fotos serão salvas (Caminho absoluto do servidor)
$UPLOAD_DIR = __DIR__ . '/uploads/fotos_pets/'; 

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $response['message'] = "Método de requisição inválido.";
    goto final_json;
}

try {
    // 1. Coleta e sanitiza dados
    $acao = $_POST['acao'] ?? ''; 
    $pet_id = (int)($_POST['id'] ?? 0); // Necessário para a edição
    $cliente_id = (int)($_POST['cliente_id'] ?? 0);
    $nome = trim($_POST['nome'] ?? '');
    $especie_id = (int)($_POST['especie_id'] ?? 0);
    
    // Tratamento de campos opcionais
    $raca_id = (int)($_POST['raca_id'] ?? 0); 
    $data_nascimento = $_POST['data_nascimento'] ?? null;
    $peso = (float)($_POST['peso'] ?? 0);
    $porte = trim($_POST['porte'] ?? ''); // Novo campo 'porte'
    
    // Checkboxes (sempre 0 ou 1)
    $castrado = isset($_POST['castrado']) ? 1 : 0;
    $vacinado = isset($_POST['vacinado']) ? 1 : 0;
    
    // Dados de foto para edição
    $foto_antiga = $_POST['foto_antiga'] ?? null; // Nome da foto que está no BD
    $remover_foto = isset($_POST['remover_foto']);
    
    $nome_arquivo_foto = $foto_antiga; // Mantém a foto antiga por padrão
    
    // =========================================================================
    // 2. VALIDAÇÃO DOS DADOS OBRIGATÓRIOS (Correção do erro "dados obrigatórios faltando")
    // =========================================================================
    if (empty($acao) || $cliente_id <= 0 || empty($nome) || $especie_id <= 0) {
        $response['message'] = "Dados obrigatórios (Ação, Cliente, Nome, Espécie) faltando.";
        goto final_json;
    }
    
    // 3. Processa o Upload da Foto
    // Esta lógica é usada para CADASTRAR e EDITAR (se uma nova foto for enviada)
    if (isset($_FILES['foto']) && $_FILES['foto']['error'] === UPLOAD_ERR_OK) {
        
        // Garante que o diretório exista e tenta configurar permissão (0777 em ambiente Linux/Server)
        if (!is_dir($UPLOAD_DIR)) {
            mkdir($UPLOAD_DIR, 0777, true);
        }

        $arquivo_temporario = $_FILES['foto']['tmp_name'];
        $nome_original = basename($_FILES['foto']['name']);
        
        // Cria um nome de arquivo único
        $extensao = pathinfo($nome_original, PATHINFO_EXTENSION);
        $nome_arquivo_foto_novo = uniqid('pet_') . '.' . $extensao;
        $caminho_destino = $UPLOAD_DIR . $nome_arquivo_foto_novo;

        // Tenta mover o arquivo
        if (move_uploaded_file($arquivo_temporario, $caminho_destino)) {
            
            // Upload bem-sucedido. Limpa a foto antiga, se houver.
            if (!empty($foto_antiga) && file_exists($UPLOAD_DIR . $foto_antiga)) {
                 unlink($UPLOAD_DIR . $foto_antiga);
            }
            
            $nome_arquivo_foto = $nome_arquivo_foto_novo;

        } else {
            error_log("Falha ao mover arquivo de foto: " . $nome_original);
            $response['message'] = "Erro ao mover arquivo de foto. Verifique as permissões da pasta 'uploads/fotos_pets/'.";
            // Continuamos o processamento do pet SEM a foto, se for edição/cadastro.
        }
    } else if ($acao === 'editar' && $remover_foto) {
        // Se a ação for EDITAR e a opção REMOVER FOTO for marcada
        if (!empty($foto_antiga) && file_exists($UPLOAD_DIR . $foto_antiga)) {
            unlink($UPLOAD_DIR . $foto_antiga);
        }
        $nome_arquivo_foto = null; // Zera o campo no banco
    }

    // 4. Tratamento final de variáveis para o Banco (NULL ou Valor)
    $raca_id_db = ($raca_id > 0) ? $raca_id : null;
    $porte_db = empty($porte) ? null : $porte;
    
    // =========================================================================
    // 5. PROCESSAMENTO: CADASTRAR (INSERT)
    // =========================================================================
    if ($acao === 'cadastrar') {
        
        $sql = "INSERT INTO pet 
            (cliente_id, nome, especie_id, raca_id, data_nascimento, peso, porte, castrado, vacinado, foto, created_at) 
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
            
        $stmt = $pdo->prepare($sql);
        
        $stmt->execute([
            $cliente_id, 
            $nome, 
            $especie_id, 
            $raca_id_db, 
            $data_nascimento ?: null, 
            $peso, 
            $porte_db,
            $castrado, 
            $vacinado, 
            $nome_arquivo_foto 
        ]);
        
        $response['success'] = true;
        $response['message'] = "Pet **" . htmlspecialchars($nome) . "** cadastrado com sucesso!";
        
    // =========================================================================
    // 6. PROCESSAMENTO: EDITAR (UPDATE)
    // =========================================================================
    } elseif ($acao === 'editar') {
        
        if ($pet_id <= 0) {
            $response['message'] = "ID do Pet para edição inválido.";
            goto final_json;
        }
        
        $sql = "UPDATE pet SET 
                    nome = ?, 
                    especie_id = ?, 
                    raca_id = ?, 
                    data_nascimento = ?, 
                    peso = ?, 
                    porte = ?,
                    castrado = ?, 
                    vacinado = ?, 
                    foto = ?,
                    updated_at = NOW() 
                WHERE id = ? AND cliente_id = ?"; // Incluindo cliente_id como segurança
                
        $stmt = $pdo->prepare($sql);
        
        $stmt->execute([
            $nome, 
            $especie_id, 
            $raca_id_db, 
            $data_nascimento ?: null, 
            $peso, 
            $porte_db,
            $castrado, 
            $vacinado, 
            $nome_arquivo_foto,
            $pet_id, // WHERE id
            $cliente_id // AND cliente_id
        ]);
        
        $response['success'] = true;
        $response['message'] = "Pet **" . htmlspecialchars($nome) . "** atualizado com sucesso!";
        
    } else {
        $response['message'] = "Ação inválida: " . htmlspecialchars($acao);
    }


} catch (\PDOException $e) {
    // Trata erros do banco de dados
    error_log("Erro no processamento de Pet: " . $e->getMessage());
    $response['message'] = "Erro de Banco de Dados: " . $e->getMessage();
    
} catch (\Exception $e) {
    // Trata outros erros
    $response['message'] = "Erro interno: " . $e->getMessage();
}


final_json:
echo json_encode($response);
exit;