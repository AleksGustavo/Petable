<?php
// Arquivo: pets_processar.php
// Objetivo: Receber e processar o formulário de cadastro/edição de Pet, incluindo o upload de foto, E AÇÃO DE EXCLUIR.

header('Content-Type: application/json');

require_once 'conexao.php'; // Assume que 'conexao.php' retorna a variável $pdo (PDO)

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

// Diretório onde as fotos serão salvas (Caminho absoluto do servidor)
$UPLOAD_DIR = __DIR__ . '/uploads/fotos_pets/'; 

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $response['message'] = "Método de requisição inválido. Esperado POST.";
    goto final_json;
}

try {
    // 1. Coleta e sanitiza dados básicos
    $acao = $_POST['acao'] ?? ''; 
    $pet_id = (int)($_POST['id'] ?? 0); 
    
    // =========================================================================
    // 2. PROCESSAMENTO DA AÇÃO
    // =========================================================================

    if ($acao === 'cadastrar' || $acao === 'editar') {
        
        // Coleta dados específicos de Cadastro/Edição
        $cliente_id = (int)($_POST['cliente_id'] ?? 0);
        $nome = trim($_POST['nome'] ?? '');
        $especie_id = (int)($_POST['especie_id'] ?? 0);
        $raca_id = (int)($_POST['raca_id'] ?? 0); 
        $data_nascimento = $_POST['data_nascimento'] ?? null;
        $peso = (float)($_POST['peso'] ?? 0);
        $porte = trim($_POST['porte'] ?? ''); 
        $castrado = isset($_POST['castrado']) ? 1 : 0;
        $vacinado = isset($_POST['vacinado']) ? 1 : 0;
        $foto_antiga = $_POST['foto_antiga'] ?? null; 
        $remover_foto = isset($_POST['remover_foto']);
        $nome_arquivo_foto = $foto_antiga; 
        
        // VALIDAÇÃO DOS DADOS OBRIGATÓRIOS para CADASTRO/EDIÇÃO
        if ($cliente_id <= 0 || empty($nome) || $especie_id <= 0) {
            $response['message'] = "Dados obrigatórios (Cliente, Nome, Espécie) faltando para " . $acao . ".";
            goto final_json;
        }

        // 3. Processa o Upload da Foto
        if (isset($_FILES['foto']) && $_FILES['foto']['error'] === UPLOAD_ERR_OK) {
            
            if (!is_dir($UPLOAD_DIR)) {
                mkdir($UPLOAD_DIR, 0777, true);
            }

            $arquivo_temporario = $_FILES['foto']['tmp_name'];
            $nome_original = basename($_FILES['foto']['name']);
            $extensao = pathinfo($nome_original, PATHINFO_EXTENSION);
            $nome_arquivo_foto_novo = uniqid('pet_') . '.' . $extensao;
            $caminho_destino = $UPLOAD_DIR . $nome_arquivo_foto_novo;

            if (move_uploaded_file($arquivo_temporario, $caminho_destino)) {
                
                // Exclui a foto antiga
                if (!empty($foto_antiga) && file_exists($UPLOAD_DIR . $foto_antiga)) {
                    unlink($UPLOAD_DIR . $foto_antiga);
                }
                
                $nome_arquivo_foto = $nome_arquivo_foto_novo;

            } else {
                error_log("Falha ao mover arquivo de foto: " . $nome_original);
                $response['message'] = "Erro ao mover arquivo de foto. Verifique as permissões da pasta 'uploads/fotos_pets/'.";
                // Continua o processamento, mas mantém a foto atual se não houver erro fatal
            }
        } else if ($acao === 'editar' && $remover_foto) {
            // Se for edição e a opção de REMOVER FOTO for marcada
            if (!empty($foto_antiga) && file_exists($UPLOAD_DIR . $foto_antiga)) {
                unlink($UPLOAD_DIR . $foto_antiga);
            }
            $nome_arquivo_foto = null; // Zera o campo no banco
        }

        // 4. Tratamento final de variáveis para o Banco (NULL ou Valor)
        $raca_id_db = ($raca_id > 0) ? $raca_id : null;
        $porte_db = empty($porte) ? null : $porte;
        
        if ($acao === 'cadastrar') {
            
            // PROCESSAMENTO: CADASTRAR (INSERT)
            $sql = "INSERT INTO pet 
                (cliente_id, nome, especie_id, raca_id, data_nascimento, peso, porte, castrado, vacinado, foto, created_at) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";
                
            $stmt = $pdo->prepare($sql);
            
            $stmt->execute([
                $cliente_id, $nome, $especie_id, $raca_id_db, $data_nascimento ?: null, 
                $peso, $porte_db, $castrado, $vacinado, $nome_arquivo_foto 
            ]);
            
            $response['success'] = true;
            $response['message'] = "Pet **" . htmlspecialchars($nome) . "** cadastrado com sucesso!";
            
        } elseif ($acao === 'editar') {
            
            // PROCESSAMENTO: EDITAR (UPDATE)
            if ($pet_id <= 0) {
                $response['message'] = "ID do Pet para edição inválido.";
                goto final_json;
            }
            
            $sql = "UPDATE pet SET 
                        nome = ?, especie_id = ?, raca_id = ?, data_nascimento = ?, 
                        peso = ?, porte = ?, castrado = ?, vacinado = ?, foto = ?, updated_at = NOW() 
                    WHERE id = ? AND cliente_id = ?"; 
                    
            $stmt = $pdo->prepare($sql);
            
            $stmt->execute([
                $nome, $especie_id, $raca_id_db, $data_nascimento ?: null, $peso, $porte_db,
                $castrado, $vacinado, $nome_arquivo_foto, $pet_id, $cliente_id 
            ]);
            
            $response['success'] = true;
            $response['message'] = "Pet **" . htmlspecialchars($nome) . "** atualizado com sucesso!";
            
        }

    // =========================================================================
    // 7. PROCESSAMENTO: EXCLUIR (DELETE) - NOVO BLOCO
    // =========================================================================
    } elseif ($acao === 'excluir') {

        if ($pet_id <= 0) {
            $response['message'] = 'ID do Pet inválido ou não fornecido para exclusão.';
            goto final_json;
        }

        // 1. Busca o nome da foto para exclusão no sistema de arquivos
        $sql_foto = "SELECT foto FROM pet WHERE id = ?";
        $stmt_foto = $pdo->prepare($sql_foto);
        $stmt_foto->execute([$pet_id]);
        $foto_data = $stmt_foto->fetch(PDO::FETCH_ASSOC);
        $foto_para_excluir = $foto_data['foto'] ?? null;
        $stmt_foto = null;

        // Inicia a transação para garantir que todas as exclusões sejam atômicas
        $pdo->beginTransaction();

        try {
            // 2. Excluir registros dependentes (Carteira de Vacina)
            $sql_del_vacinas = "DELETE FROM carteira_vacina WHERE pet_id = ?";
            $stmt_del_vacinas = $pdo->prepare($sql_del_vacinas);
            $stmt_del_vacinas->execute([$pet_id]);

            // 3. Excluir registros dependentes (Atendimentos - Se houver)
            // Descomente e ajuste se a tabela 'atendimento' existir e referenciar 'pet_id'
            // $sql_del_atendimentos = "DELETE FROM atendimento WHERE pet_id = ?";
            // $stmt_del_atendimentos = $pdo->prepare($sql_del_atendimentos);
            // $stmt_del_atendimentos->execute([$pet_id]);

            // 4. Excluir o Pet principal
            $sql_del_pet = "DELETE FROM pet WHERE id = ?";
            $stmt_del_pet = $pdo->prepare($sql_del_pet);
            $stmt_del_pet->execute([$pet_id]);

            if ($stmt_del_pet->rowCount() > 0) {
                $pdo->commit();

                // 5. Excluir o arquivo de foto do servidor (após a confirmação do DB)
                if (!empty($foto_para_excluir) && file_exists($UPLOAD_DIR . $foto_para_excluir)) {
                    unlink($UPLOAD_DIR . $foto_para_excluir);
                    $response['message'] = "Pet (ID: {$pet_id}) e seus registros foram excluídos com sucesso!";
                } else {
                    $response['message'] = "Pet (ID: {$pet_id}) e seus registros foram excluídos com sucesso.";
                }

                $response['success'] = true;
            } else {
                $pdo->rollBack();
                $response['message'] = "Erro: Nenhum Pet encontrado com o ID: {$pet_id} para exclusão.";
            }

        } catch (\PDOException $e) {
            $pdo->rollBack();
            throw $e; // Re-lança para ser pego pelo catch principal
        }

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