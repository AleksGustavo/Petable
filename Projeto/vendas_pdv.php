<?php
require_once 'conexao.php'; 

$clientes = [];
try {
    // Busca todos os clientes ativos
    $sql_clientes = "SELECT id, nome FROM cliente WHERE ativo = 1 ORDER BY nome ASC";
    $stmt_clientes = $pdo->query($sql_clientes);
    $clientes = $stmt_clientes->fetchAll(PDO::FETCH_ASSOC);
} catch (\PDOException $e) {
    error_log("Erro ao carregar clientes: " . $e->getMessage());
}
?>

<style>
    .ui-autocomplete {
        z-index: 1050; 
        max-height: 200px;
        overflow-y: auto;
        overflow-x: hidden;
    }

    /* ------------------------------------------- */
    /* Estilos para o Card de Sucesso e Efeito Blur */
    /* ------------------------------------------- */

    /* Overlay (fundo escuro transparente) */
    #modal-overlay {
        display: none; /* Começa escondido */
        position: fixed;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: rgba(0, 0, 0, 0.6); /* Fundo escuro */
        z-index: 1059; /* Abaixo do card de sucesso (1060) */
    }
    
    /* Classe para aplicar o efeito blur ao conteúdo principal */
    .blur-effect {
        /* Aplica o desfoque ao fundo */
        filter: blur(4px); 
        /* Transição suave para um visual mais profissional */
        transition: filter 0.3s ease-out; 
    }

    /* Card de Sucesso - Posição e Animação */
    #sucesso-venda-card {
        position: fixed;
        top: 50%;
        left: 50%;
        transform: translate(-50%, -50%);
        z-index: 1060;
        width: 300px;
        border-radius: 15px;
        /* Adiciona uma animação suave de brilho (pulse-shadow) */
        animation: pulse-shadow 1.5s infinite; 
    }

    /* Animação de Brilho */
    @keyframes pulse-shadow {
        0% { box-shadow: 0 0 5px rgba(0, 128, 0, 0.4); }
        50% { box-shadow: 0 0 15px rgba(0, 128, 0, 0.8); }
        100% { box-shadow: 0 0 5px rgba(0, 128, 0, 0.4); }
    }
    
    /* Animação de Bounce para o Ícone */
    @keyframes bounce {
        0%, 100% { transform: translateY(0); }
        50% { transform: translateY(-10px); }
    }
    
</style>

<div id="modal-overlay"></div>

<div id="sucesso-venda-card" class="card text-center bg-light shadow-lg" style="display: none;">
    <div class="card-body p-4">
        <h4 class="card-title text-success mb-3">Venda Realizada com Sucesso!</h4>
        
        <i class="fas fa-check-circle fa-4x text-success mb-3" style="animation: bounce 0.5s ease-in-out infinite alternate;"></i>

        <p class="card-text text-muted mb-0" id="mensagem-sucesso-venda"></p>
        <button type="button" class="btn btn-sm btn-success mt-3" onclick="fecharSucesso();">Continuar Vendas</button>
    </div>
</div>
<div id="main-content-wrapper">

    <div class="d-flex justify-content-between align-items-center mb-2">
        <h2><i class="fas fa-cash-register me-2"></i> Ponto de Venda (PDV)</h2>
        
        <div>
            <button type="button" class="btn btn-info btn-sm" id="btn-fechar-caixa">
                <i class="fas fa-lock me-1"></i> Fechar Caixa
            </button>
        </div>
    </div>

    <div id="status-message-area">
    </div>

    <form id="form-pdv" method="POST" action="vendas_processar.php">
        <div class="row g-2">
            
            <div class="col-lg-8">
                <div class="card shadow-sm mb-2">
                    <div class="card-header bg-primary text-white p-2">
                        <h6 class="mb-0"><i class="fas fa-search me-2"></i> Adicionar Item</h6>
                        </div>
                    <div class="card-body p-2">
                        <div class="row g-2">
                            <div class="col-md-7">
                                <label for="busca_item" class="form-label mb-1" style="font-size: 0.8rem;">Produto/Serviço (Cód. Barras / Nome)</label>
                                <input type="text" id="busca_item" name="busca_item" class="form-control form-control-sm" placeholder="Buscar por nome ou código...">
                                <input type="hidden" id="item_selecionado_id" name="item_selecionado_id">
                            </div>
                            <div class="col-md-2">
                                <label for="quantidade_item" class="form-label mb-1" style="font-size: 0.8rem;">Qtde.</label>
                                <input type="number" id="quantidade_item" name="quantidade_item" class="form-control form-control-sm" value="1" min="1">
                            </div>
                            <div class="col-md-3 d-flex align-items-end">
                                <button type="button" class="btn btn-success btn-sm w-100" id="btn-adicionar-item" disabled>
                                    <i class="fas fa-cart-plus me-1"></i> Adicionar
                                </button>
                            </div>
                        </div>
                    </div>
                </div>

                <div class="card shadow-sm">
                    <div class="card-header bg-secondary text-white p-2">
                        <h6 class="mb-0"><i class="fas fa-list me-2"></i> Itens da Venda (<span id="contador_itens">0</span>)</h6>
                    </div>
                    <div class="card-body p-0">
                        <div class="table-responsive" id="area-itens-scroll">
                            <table class="table table-striped table-hover mb-0">
                                <thead>
                                    <tr>
                                        <th>#</th>
                                        <th>Item</th>
                                        <th>Qtde.</th>
                                        <th>Preço Un.</th>
                                        <th>Subtotal</th>
                                        <th>Ação</th>
                                    </tr>
                                </thead>
                                <tbody id="tabela_itens_venda">
                                    <tr id="linha-vazia">
                                        <td colspan="6" class="text-center text-muted">Nenhum produto ou serviço adicionado.</td>
                                    </tr>
                                </tbody>
                            </table>
                        </div>
                    </div>
                </div>
            </div>
            
            <div class="col-lg-4">
                <div class="card p-2 shadow-sm">
                    <h6 class="mb-2 text-primary"><i class="fas fa-user-tag me-2"></i> Cliente</h6>
                    
                    <div class="mb-2">
                        <label for="cliente_id" class="form-label mb-1" style="font-size: 0.8rem;">Cliente (Tabela `cliente`)</label>
                        <select id="cliente_id" name="cliente_id" class="form-select form-select-sm">
                            <option value="">(Venda Anônima)</option>
                            <?php 
                            // ITERAÇÃO DINÂMICA DE CLIENTES
                            foreach ($clientes as $cliente): 
                                $label = $cliente['id'] . ' - ' . htmlspecialchars($cliente['nome']);
                            ?>
                            <option value="<?php echo $cliente['id']; ?>"><?php echo $label; ?></option>
                            <?php endforeach; ?>
                        </select>
                    </div>
                    
                    <hr class="my-2">

                    <h6 class="mb-2 text-primary"><i class="fas fa-file-invoice-dollar me-2"></i> Pagamento</h6>
                    
                    <div class="mb-2">
                        <label for="desconto_percentual" class="form-label mb-1" style="font-size: 0.8rem;">Desconto (%)</label>
                        <div class="input-group input-group-sm">
                            <input type="number" id="desconto_percentual" class="form-control" value="0" min="0" max="100" step="0.01">
                            <span class="input-group-text">%</span>
                        </div>
                        <input type="hidden" id="desconto_valor" name="desconto" value="0.00">
                        <small class="text-muted" id="desconto_valor_display" style="font-size: 0.7rem;">R$ 0,00 descontados</small>
                    </div>
                    <div class="mb-2">
                        <label for="forma_pagamento" class="form-label mb-1" style="font-size: 0.8rem;">Forma de Pagamento</label>
                        <select id="forma_pagamento" name="forma_pagamento" class="form-select form-select-sm" required>
                            <option value="">Selecione...</option>
                            <option value="pix">PIX</option>
                            <option value="cartao_credito">Cartão de Crédito</option>
                            <option value="cartao_debito">Cartão de Débito</option>
                            <option value="dinheiro">Dinheiro</option>
                            <option value="transferencia">Transferência</option>
                        </select>
                    </div>

                    <div class="mb-2">
                        <label for="observacoes" class="form-label mb-1" style="font-size: 0.8rem;">Obs.</label>
                        <textarea id="observacoes" name="observacoes" class="form-control form-control-sm" rows="1"></textarea>
                        </div>
                    
                    <hr class="my-2">

                    <div class="d-flex justify-content-between align-items-center bg-dark text-white p-2 rounded mb-3">
                        <h5 class="mb-0">TOTAL:</h5>
                        <h5 class="mb-0" id="valor_total_display">R$ 0,00</h5>
                        <input type="hidden" id="valor_total" name="valor_total" value="0.00">
                        <input type="hidden" id="itens_venda_json" name="itens_venda_json">
                    </div>

                    <button type="submit" class="btn btn-danger w-100" id="btn-finalizar-venda" disabled>
                        <i class="fas fa-check-circle me-2"></i> FINALIZAR VENDA
                    </button>
                    
                    <small class="text-muted mt-1 text-center" style="font-size: 0.7rem;">O Funcionário logado será registrado automaticamente.</small>
                </div>
            </div>
        </div>
    </form>
    
</div>
<link rel="stylesheet" href="//code.jquery.com/ui/1.13.2/themes/base/jquery-ui.css">

<script src="https://code.jquery.com/jquery-3.7.1.min.js"></script> 
<script src="//code.jquery.com/ui/1.13.2/jquery-ui.js"></script> 

<script>
    // Usa jQuery(document).ready para garantir que o jQuery UI funcione
    jQuery(document).ready(function($) {
        
        const tabelaItens = document.getElementById('tabela_itens_venda');
        const valorTotalInput = document.getElementById('valor_total');
        const valorTotalDisplay = document.getElementById('valor_total_display');
        const btnFinalizar = document.getElementById('btn-finalizar-venda');
        
        const buscaItemInput = $('#busca_item'); 
        const itemSelecionadoId = document.getElementById('item_selecionado_id');
        const btnAdicionar = document.getElementById('btn-adicionar-item');
        const quantidadeItemInput = document.getElementById('quantidade_item');
        const itensVendaJson = document.getElementById('itens_venda_json');
        
        // NOVAS VARIÁVEIS PARA O EFEITO BLUR E OVERLAY
        const sucessoCard = $('#sucesso-venda-card');
        const overlay = $('#modal-overlay');
        const mainContent = $('#main-content-wrapper');

        // NOVAS VARIÁVEIS PARA O DESCONTO (%)
        const descontoPercentualInput = document.getElementById('desconto_percentual');
        const descontoValorInput = document.getElementById('desconto_valor'); // Campo HIDDEN name="desconto"
        const descontoValorDisplay = document.getElementById('desconto_valor_display'); // Display R$ descontado


        // Variável local para armazenar os dados do item da venda
        let itensVenda = []; 

        // FUNÇÃO GLOBAL PARA FECHAR O CARD E REMOVER O BLUR
        window.fecharSucesso = function() {
            sucessoCard.fadeOut(200, function() {
                overlay.hide();
                mainContent.removeClass('blur-effect');
                buscaItemInput.focus(); // Retorna o foco para a busca
            });
        };

        function calcularTotal() {
            let subtotalGeral = 0;
            itensVenda.forEach(item => {
                subtotalGeral += item.quantidade * item.preco;
            });

            // ----------------------------------------------------
            // NOVO: CALCULA O DESCONTO EM R$ BASEADO NA PORCENTAGEM
            // ----------------------------------------------------
            let percentual = parseFloat(descontoPercentualInput.value) || 0;
            
            // Garante que o percentual esteja entre 0 e 100
            percentual = Math.min(100, Math.max(0, percentual));
            
            let descontoValor = (subtotalGeral * (percentual / 100));
            
            // Armazena o valor do desconto (R$) no campo oculto (name="desconto")
            descontoValorInput.value = descontoValor.toFixed(2);
            descontoValorDisplay.textContent = 'R$ ' + descontoValor.toFixed(2).replace('.', ',') + ' descontados';
            // ----------------------------------------------------

            let totalFinal = Math.max(0, subtotalGeral - descontoValor);

            valorTotalInput.value = totalFinal.toFixed(2);
            valorTotalDisplay.textContent = 'R$ ' + totalFinal.toFixed(2).replace('.', ',');
            
            btnFinalizar.disabled = itensVenda.length === 0 || totalFinal <= 0;
            document.getElementById('contador_itens').textContent = itensVenda.length;
            
            // ATUALIZA O JSON PARA ENVIO NO FORMULÁRIO (CRÍTICO para o backend)
            itensVendaJson.value = JSON.stringify(itensVenda);
        }
        
        // FUNÇÃO PRINCIPAL: ADICIONA ITEM SELECIONADO À LISTA
        function adicionarItem(item) {
            const quantidade = parseInt(quantidadeItemInput.value);

            if (!item || quantidade <= 0) {
                return;
            }
            
            // Verifica se o item (produto/serviço) já está na lista
            const itemExistente = itensVenda.find(i => 
                i.id === item.id && i.tipo === item.tipo
            );

            if (itemExistente) {
                itemExistente.quantidade += quantidade;
            } else {
                const novoItem = {
                    id: item.id,       // ID do produto ou serviço (do banco)
                    nome: item.nome,   // Nome completo
                    preco: item.preco, // Preço unitário (do banco)
                    quantidade: quantidade,
                    tipo: item.tipo,   // 'produto' ou 'servico'
                    codigo_barras: item.codigo_barras || null 
                };
                itensVenda.push(novoItem);
            }
            
            renderizarItens();
            
            // Limpa os campos de busca após adicionar
            buscaItemInput.val('');
            itemSelecionadoId.value = '';
            quantidadeItemInput.value = 1;
            btnAdicionar.disabled = true;
            buscaItemInput.removeData('selectedItem'); 

            // Foca na busca para o próximo item
            buscaItemInput.focus();
        }

        // ADICIONA EVENTO AO BOTÃO "ADICIONAR"
        btnAdicionar.addEventListener('click', function() {
            const selectedItem = buscaItemInput.data('selectedItem');
            if (selectedItem && itemSelecionadoId.value) {
                adicionarItem(selectedItem);
            } else {
                alert("Por favor, selecione um item da lista de sugestões.");
            }
        });

        // Simulação da função de remover item
        window.removerItem = function(id, tipo) {
            // Remove o item baseado na combinação ID e TIPO
            itensVenda = itensVenda.filter(item => !(item.id === id && item.tipo === tipo));
            renderizarItens();
        };

        function renderizarItens() {
            tabelaItens.innerHTML = '';
            if (itensVenda.length === 0) {
                tabelaItens.innerHTML = '<tr id="linha-vazia"><td colspan="6" class="text-center text-muted">Nenhum produto ou serviço adicionado.</td></tr>';
            } else {
                itensVenda.forEach((item, index) => {
                    const subtotal = item.quantidade * item.preco;
                    const tipoLabel = item.tipo === 'servico' ? '<i class="fas fa-tools text-info me-1"></i>' : '';
                    
                    tabelaItens.innerHTML += `
                        <tr>
                            <td>${index + 1}</td>
                            <td>${tipoLabel} ${item.nome}</td>
                            <td>
                                <input type="number" 
                                       data-item-id="${item.id}" 
                                       data-item-tipo="${item.tipo}" 
                                       value="${item.quantidade}" 
                                       min="1" 
                                       class="form-control form-control-sm text-center input-quantidade-pdv" 
                                       style="width: 60px;">
                            </td>
                            <td>R$ ${item.preco.toFixed(2).replace('.', ',')}</td>
                            <td>R$ ${subtotal.toFixed(2).replace('.', ',')}</td>
                            <td>
                                <button type="button" class="btn btn-sm btn-danger p-1" onclick="removerItem(${item.id}, '${item.tipo}')">
                                    <i class="fas fa-trash-alt"></i>
                                </button>
                            </td>
                        </tr>
                    `;
                });
            }
            calcularTotal();
        }
        
        // EVENTO PARA ALTERAR QUANTIDADE DIRETO NA TABELA
        $(document).on('change', '.input-quantidade-pdv', function() {
            const itemId = parseInt($(this).data('item-id'));
            const itemTipo = $(this).data('item-tipo');
            const novaQuantidade = parseInt($(this).val());
            
            if (novaQuantidade > 0) {
                const item = itensVenda.find(i => i.id === itemId && i.tipo === itemTipo);
                if (item) {
                    item.quantidade = novaQuantidade;
                    renderizarItens();
                }
            } else {
                window.removerItem(itemId, itemTipo);
            }
        });

        // NOVO EVENT LISTENER: OUVINDO O CAMPO DE PORCENTAGEM
        descontoPercentualInput.addEventListener('input', calcularTotal); 
        calcularTotal();
        
        /* --------------------------------------------------
         * LÓGICA DO AUTOCOMPLETE AJAX
         * -------------------------------------------------- */
        
        buscaItemInput.autocomplete({
            minLength: 2, 
            source: "pdv_buscar_itens.php", // Arquivo PHP que retorna o JSON
            
            select: function(event, ui) {
                const item = ui.item;
                
                buscaItemInput.val(item.nome);
                itemSelecionadoId.value = `${item.id}-${item.tipo}`;
                buscaItemInput.data('selectedItem', item); 
                btnAdicionar.disabled = false;
                quantidadeItemInput.focus(); 

                return false; 
            },
            focus: function(event, ui) {
                buscaItemInput.val(ui.item.nome); 
                return false;
            }
        }).autocomplete("instance")._renderItem = function(ul, item) {
            return $("<li>")
                .append(`<div>${item.label}</div>`)
                .appendTo(ul);
        };
        
        buscaItemInput.on('input', function() {
            if ($(this).val() !== buscaItemInput.data('selectedItem')?.nome) {
                itemSelecionadoId.value = '';
                btnAdicionar.disabled = true;
                buscaItemInput.removeData('selectedItem'); 
            }
        });
        
        buscaItemInput.keypress(function(e) {
            if (e.which === 13) { 
                e.preventDefault();
                if (!btnAdicionar.disabled) {
                    btnAdicionar.click();
                }
            }
        });
        
        /* --------------------------------------------------
         * LÓGICA DE FINALIZAR VENDA (Submissão do Formulário)
         * -------------------------------------------------- */
        $('#form-pdv').on('submit', function(e) {
            e.preventDefault();
            
            if (itensVenda.length === 0) {
                alert('Adicione itens à venda antes de finalizar.');
                return;
            }

            // Garante que o total está calculado com o desconto % antes de enviar
            calcularTotal();
            
            const form = $(this);
            const statusArea = $('#status-message-area');
            
            // Desabilita o botão para evitar cliques duplos
            btnFinalizar.disabled = true;

            $.ajax({
                url: form.attr('action'), // vendas_processar.php
                type: 'POST',
                data: form.serialize(), // Envia todos os dados do formulário, incluindo o JSON e o desconto em R$
                dataType: 'json',
                success: function(response) {
                    // Limpa e oculta a área de mensagens simples (erros)
                    statusArea.empty().hide(); 

                    if (response.success) {
                        // 1. Aplica o efeito blur no conteúdo principal e exibe o overlay
                        mainContent.addClass('blur-effect');
                        overlay.show();
                        
                        // 2. Exibe a mensagem da venda no card animado
                        $('#mensagem-sucesso-venda').text(response.message);
                        sucessoCard.fadeIn(300); 

                        // 3. Limpar a interface após a venda bem-sucedida
                        itensVenda = [];
                        renderizarItens(); // Zera a tabela e recalcula o total
                        // Reset do formulário, limpando também o campo de desconto %
                        form[0].reset(); 
                        
                    } else {
                        // Se falhar, exibe a mensagem de erro normal e reabilita o botão
                        statusArea.html('<div class="alert alert-danger">' + response.message + '</div>').show();
                        btnFinalizar.disabled = false;
                    }
                },
                error: function(xhr, status, error) {
                    statusArea.html('<div class="alert alert-danger">Erro de comunicação com o servidor. Tente novamente.</div>').show();
                    btnFinalizar.disabled = false;
                }
            });
        });

    });
</script>