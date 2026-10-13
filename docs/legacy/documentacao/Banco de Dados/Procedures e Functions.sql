SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;


DELIMITER $$

/* Título: procedure_cadastrar_cliente

Objetivo: Inserir um novo registro completo de cliente na tabela 'cliente', incluindo dados pessoais e de endereço.
*/

CREATE DEFINER=`root`@`localhost` PROCEDURE `procedure_cadastrar_cliente` (IN `p_nome` VARCHAR(100), IN `p_cpf` VARCHAR(14), IN `p_data_nascimento` DATE, IN `p_telefone` VARCHAR(15), IN `p_email` VARCHAR(100), IN `p_cep` VARCHAR(10), IN `p_rua` VARCHAR(150), IN `p_bairro` VARCHAR(100), IN `p_numero` VARCHAR(20), IN `p_complemento` VARCHAR(100), IN `p_estado` VARCHAR(20), IN `p_observacoes` TEXT)  BEGIN
    INSERT INTO `cliente` (
        `nome`, 
        `cpf`, 
        `data_nascimento`, 
        `telefone`, 
        `email`, 
        `cep`,
        `rua`, 
        `bairro`, 
        `numero`, 
        `complemento`,
        `estado`,
        `observacoes`
    ) 
    VALUES (
        p_nome, 
        p_cpf, 
        p_data_nascimento, 
        p_telefone, 
        p_email, 
        p_cep,
        p_rua, 
        p_bairro, 
        p_numero, 
        p_complemento,
        p_estado,
        p_observacoes
    );
END$$


/* Título: procedure_cadastrar_pet

Objetivo: Inserir um novo registro de animal de estimação (pet) na tabela 'pet', vinculando-o a um cliente
e fornecendo detalhes como espécie, raça e status de saúde. Retorna o ID do pet recém-criado.
*/

CREATE DEFINER=`root`@`localhost` PROCEDURE `procedure_cadastrar_pet` (IN `p_nome` VARCHAR(50), IN `p_cliente_id` INT, IN `p_especie_id` INT, IN `p_raca_id` INT, IN `p_data_nascimento` DATE, IN `p_peso` DECIMAL(5,2), IN `p_cor` VARCHAR(30), IN `p_castrado` TINYINT, IN `p_vacinado` TINYINT, IN `p_porte` ENUM('Pequeno','Medio','Grande'), IN `p_observacoes` TEXT, IN `p_foto` VARCHAR(255))   BEGIN
    INSERT INTO `pet` (
        `nome`,
        `cliente_id`,
        `especie_id`,
        `raca_id`,
        `data_nascimento`,
        `peso`,
        `cor`,
        `castrado`,
        `vacinado`,
        `porte`,
        `observacoes`,
        `foto`
    )
    VALUES (
        p_nome,
        p_cliente_id,
        p_especie_id,
        p_raca_id,
        p_data_nascimento,
        p_peso,
        p_cor,
        p_castrado,
        p_vacinado,
        p_porte,
        p_observacoes,
        p_foto
    );
    -- Retorna o ID do pet recém-criado
    SELECT LAST_INSERT_ID() AS id_pet_cadastrado;
END$$


/* Título: procedure_cadastrar_produto

Objetivo: Inserir um novo item na tabela 'produto', detalhando nome, descrição, categoria, fornecedor, preços
e dados de controle de estoque. Retorna o ID do produto recém-criado.
*/

CREATE DEFINER=`root`@`localhost` PROCEDURE `procedure_cadastrar_produto` (IN `p_nome` VARCHAR(100), IN `p_descricao` TEXT, IN `p_categoria_id` INT, IN `p_fornecedor_padrao_id` INT, IN `p_preco_custo` DECIMAL(10,2), IN `p_preco_venda` DECIMAL(10,2), IN `p_quantidade_estoque` INT, IN `p_estoque_minimo` INT, IN `p_codigo_barras` VARCHAR(50))   BEGIN
    INSERT INTO `produto` (
        `nome`,
        `descricao`,
        `categoria_id`,
        `fornecedor_padrao_id`,
        `preco_custo`,
        `preco_venda`,
        `quantidade_estoque`,
        `estoque_minimo`,
        `codigo_barras`
    )
    VALUES (
        p_nome,
        p_descricao,
        p_categoria_id,
        p_fornecedor_padrao_id,
        p_preco_custo,
        p_preco_venda,
        p_quantidade_estoque,
        p_estoque_minimo,
        p_codigo_barras
    );
    -- Retorna o ID do produto recém-criado
    SELECT LAST_INSERT_ID() AS id_produto_cadastrado;
END$$


/* Título: procedure_registrar_fornecedor

Objetivo: Inserir um novo registro de fornecedor na tabela 'fornecedor', incluindo informações de contato, CNPJ e endereço.
*/

CREATE DEFINER=`root`@`localhost` PROCEDURE `procedure_registrar_fornecedor` (IN `p_nome_fantasia` VARCHAR(100), IN `p_razao_social` VARCHAR(150), IN `p_cnpj` VARCHAR(18), IN `p_telefone` VARCHAR(15), IN `p_email` VARCHAR(100), IN `p_contato` VARCHAR(100), IN `p_cep` VARCHAR(10), IN `p_rua` VARCHAR(150), IN `p_bairro` VARCHAR(100), IN `p_numero` VARCHAR(20), IN `p_complemento` VARCHAR(100), IN `p_observacoes` TEXT)   BEGIN
    INSERT INTO `fornecedor` (
        `nome_fantasia`, `razao_social`, `cnpj`, `telefone`, `email`,
        `contato`, `cep`, `rua`, `bairro`, `numero`, `complemento`, `observacoes`
    ) VALUES (
        p_nome_fantasia, p_razao_social, p_cnpj, p_telefone, p_email,
        p_contato, p_cep, p_rua, p_bairro, p_numero, p_complemento, p_observacoes
    );
END$$


/* Título: procedure_registrar_vacina

Objetivo: Registrar a aplicação de uma vacina na tabela 'carteira_vacina' para um pet específico, 
registrando datas de aplicação e reforço, e atualizando o status de vacinação do pet.
*/

CREATE DEFINER=`root`@`localhost` PROCEDURE `procedure_registrar_vacina` (IN `p_pet_id` INT, IN `p_nome_vacina` VARCHAR(100), IN `p_data_aplicacao` DATE, IN `p_data_proxima` DATE, IN `p_veterinario` VARCHAR(100), IN `p_observacoes` TEXT)   BEGIN
    -- INSERÇÃO na tabela CARTEIRA_VACINA (tabela presumida com base na lógica original)
    INSERT INTO `carteira_vacina` (
        `pet_id`, `nome_vacina`, `data_aplicacao`, `data_proxima`, `veterinario`, `observacoes`
    ) VALUES (
        p_pet_id, p_nome_vacina, p_data_aplicacao, p_data_proxima, p_veterinario, p_observacoes
    );

    -- Atualiza o indicador 'vacinado' na tabela PET (tabela presumida com base na lógica original)
    UPDATE `pet`
    SET `vacinado` = 1
    WHERE `id` = p_pet_id;
END$$


/* Título: proxima_vacina_pet

Objetivo: Função que retorna a data mais próxima para a próxima dose ou reforço de vacina de um pet,
filtrando apenas datas futuras ou a data atual.
*/

CREATE DEFINER=`root`@`localhost` FUNCTION `proxima_vacina_pet` (`p_pet_id` INT) RETURNS DATE READS SQL DATA BEGIN
    DECLARE v_proxima_data DATE;

    -- Busca a menor data futura de próxima dose na carteira de vacinação
    SELECT MIN(data_proxima)
    INTO v_proxima_data
    FROM carteira_vacina
    WHERE pet_id = p_pet_id
    AND data_proxima IS NOT NULL
    AND data_proxima >= CURDATE();

    RETURN v_proxima_data;
END$$


/* Título: total_servicos_agendamento

Objetivo: Função que calcula e retorna o valor total de todos os serviços realizados vinculados a um agendamento específico.
Retorna 0.00 se nenhum serviço for encontrado.
*/

CREATE DEFINER=`root`@`localhost` FUNCTION `total_servicos_agendamento` (`p_agendamento_id` INT) RETURNS DECIMAL(10,2) READS SQL DATA BEGIN
    DECLARE v_total DECIMAL(10,2);

    -- Soma o valor de todos os serviços realizados que referenciam este agendamento
    SELECT SUM(valor)
    INTO v_total
    FROM servico_realizado
    WHERE agendamento_id = p_agendamento_id;

    -- Retorna 0.00 se a soma for nula (se não houver serviços realizados para este agendamento)
    RETURN IFNULL(v_total, 0.00);
END$$

DELIMITER ;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;