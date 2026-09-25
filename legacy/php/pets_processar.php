<?php
header('Content-Type: application/json');

require_once 'conexao.php';

$response = [
    'success' => false,
    'message' => 'Erro desconhecido.'
];

$UPLOAD_DIR = __DIR__ . '/uploads/fotos_pets/'; 

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    $response['message'] = "Método de requisição inválido. Esperado POST.";
    goto final_json;
}

try {
    $acao = $_POST['acao'] ?? ''; 
    $pet_id = (int)($_POST['id'] ?? 0); 
    
    if ($acao === 'cadastrar' || $acao === 'editar') {
        
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
        
        if ($cliente_id <= 0 || empty($nome) || $especie_id <= 0) {
            $response['message'] = "Dados obrigatórios (Cliente, Nome, Espécie) faltando para " . $acao . ".";
            goto final_json;
        }

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
                
                if (!empty($foto_antiga) && file_exists($UPLOAD_DIR . $foto_antiga)) {
                    unlink($UPLOAD_DIR . $foto_antiga);
                }
                
                $nome_arquivo_foto = $nome_arquivo_foto_novo;

            } else {
                error_log("Falha ao mover arquivo de foto: " . $nome_original);
                $response['message'] = "Erro ao mover arquivo de foto. Verifique as permissões da pasta 'uploads/fotos_pets/'.";
            }
        } else if ($acao === 'editar' && $remover_foto) {
            if (!empty($foto_antiga) && file_exists($UPLOAD_DIR . $foto_antiga)) {
                unlink($UPLOAD_DIR . $foto_antiga);
            }
            $nome_arquivo_foto = null;
        }

        $raca_id_db = ($raca_id > 0) ? $raca_id : null;
        $porte_db = empty($porte) ? null : $porte;
        
        if ($acao === 'cadastrar') {
            
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

    } elseif ($acao === 'excluir') {

        if ($pet_id <= 0) {
            $response['message'] = 'ID do Pet inválido ou não fornecido para exclusão.';
            goto final_json;
        }

        $sql_foto = "SELECT foto FROM pet WHERE id = ?";
        $stmt_foto = $pdo->prepare($sql_foto);
        $stmt_foto->execute([$pet_id]);
        $foto_data = $stmt_foto->fetch(PDO::FETCH_ASSOC);
        $foto_para_excluir = $foto_data['foto'] ?? null;
        $stmt_foto = null;

        $pdo->beginTransaction();

        try {
            $sql_del_vacinas = "DELETE FROM carteira_vacina WHERE pet_id = ?";
            $stmt_del_vacinas = $pdo->prepare($sql_del_vacinas);
            $stmt_del_vacinas->execute([$pet_id]);

            // $sql_del_atendimentos = "DELETE FROM atendimento WHERE pet_id = ?";
            // $stmt_del_atendimentos = $pdo->prepare($sql_del_atendimentos);
            // $stmt_del_atendimentos->execute([$pet_id]);

            $sql_del_pet = "DELETE FROM pet WHERE id = ?";
            $stmt_del_pet = $pdo->prepare($sql_del_pet);
            $stmt_del_pet->execute([$pet_id]);

            if ($stmt_del_pet->rowCount() > 0) {
                $pdo->commit();

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
            throw $e;
        }

    } else {
        $response['message'] = "Ação inválida: " . htmlspecialchars($acao);
    }


} catch (\PDOException $e) {
    error_log("Erro no processamento de Pet: " . $e->getMessage());
    $response['message'] = "Erro de Banco de Dados: " . $e->getMessage();
    
} catch (\Exception $e) {
    $response['message'] = "Erro interno: " . $e->getMessage();
}


final_json:
echo json_encode($response);
exit;
?>