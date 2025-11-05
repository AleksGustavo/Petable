<?php
// Arquivo: search_data.php

// Inclui o arquivo de conexão
require_once 'conexao.php'; 

// Define o cabeçalho para retornar JSON
header('Content-Type: application/json');

$type = $_GET['type'] ?? '';
$data = [];

// ===========================================
// LÓGICA DE BUSCA DE CLIENTE (type=client)
// ===========================================
if ($type === 'client') {
    $termo = $_GET['term'] ?? '';
    if (strlen($termo) >= 3) {
        // Protege contra SQL Injection
        $termo_seguro = mysqli_real_escape_string($conexao, '%' . $termo . '%');

        // Busca por Nome, CPF ou Telefone (o Telefone estava no placeholder original, vamos manter a flexibilidade)
        $sql_cliente = "SELECT id, nome, cpf, telefone 
                        FROM cliente 
                        WHERE nome LIKE '{$termo_seguro}' 
                        OR cpf LIKE '{$termo_seguro}' 
                        OR telefone LIKE '{$termo_seguro}'
                        LIMIT 10"; 
        
        $result_cliente = mysqli_query($conexao, $sql_cliente);
        if ($result_cliente) {
            $data = mysqli_fetch_all($result_cliente, MYSQLI_ASSOC);
        }
    }
}

// ===========================================
// LÓGICA DE BUSCA DE PETS (type=pet)
// ===========================================
elseif ($type === 'pet') {
    $clienteId = $_GET['client_id'] ?? 0;
    $clienteId = (int)$clienteId;

    if ($clienteId > 0) {
        // Busca pets ativos do cliente, incluindo o nome da raça e o porte (nova coluna)
        $sql_pet = "SELECT p.id, p.nome, p.porte, r.nome as raca_nome
                    FROM pet p
                    LEFT JOIN raca r ON p.raca_id = r.id
                    WHERE p.cliente_id = {$clienteId} AND p.ativo = 1
                    ORDER BY p.nome ASC";

        $result_pet = mysqli_query($conexao, $sql_pet);
        if ($result_pet) {
            $data = mysqli_fetch_all($result_pet, MYSQLI_ASSOC);
        }
    }
}

mysqli_close($conexao);

// Retorna o resultado para o JavaScript
echo json_encode($data);
?>