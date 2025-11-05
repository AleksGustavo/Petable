<?php
//redireciona para o login, tipo um loop 
header("Location: login.php");
exit(); 
?>

<script>
    
    // ... Código da função inicializarMascarasEValidacoes ... (MANTIDO)
    function inicializarMascarasEValidacoes() {
        // ... (Seu código de máscaras e validações) ...
        $('.mask-cpf').mask('000.000.000-00');
        // ... (restante das suas máscaras e validações) ...
    }


    // ... Código da função carregarConteudo(pagina, pushHistory = true) ... (MANTIDO)
    function carregarConteudo(pagina, pushHistory = true) {
        // ... (Seu código de requisição AJAX para carregar páginas) ...
        // ... (MANTENHA ESTE CÓDIGO INALTERADO) ...
        $('#conteudo-dinamico').html('<div class="text-center mt-5"><i class="fas fa-spinner fa-spin fa-3x text-primary"></i><p class="mt-2 text-muted">Carregando...</p></div>');
        
        $.ajax({
            url: pagina, 
            type: 'GET',
            success: function(data) {
                $('#conteudo-dinamico').html(data);
                inicializarMascarasEValidacoes(); 
                
                if (pushHistory) {
                    let tituloBase = 'Gerenciamento';
                    let paginaFormatada = pagina.replace('.php', '').split('_').map(word => word.charAt(0).toUpperCase() + word.slice(1)).join(' ');
                    let titulo = `${tituloBase} - ${paginaFormatada}`;
                    
                    history.pushState({pagina: pagina}, titulo, `?p=${pagina}`);
                    document.title = titulo;
                }
                
                $('.nav-link[data-pagina]').removeClass('active');
                $(`.nav-link[data-pagina="${pagina}"]`).addClass('active');

                const $activeLink = $(`.nav-link[data-pagina="${pagina}"]`);
                const $parentCollapse = $activeLink.closest('.collapse');
                if ($parentCollapse.length) {
                    const collapseInstance = bootstrap.Collapse.getOrCreateInstance($parentCollapse.get(0));
                    collapseInstance.show();
                }
            },
            error: function(xhr, status, error) {
                $('#conteudo-dinamico').html('<div class="alert alert-danger">Erro ao carregar o conteúdo da página ' + pagina + '. Status: ' + xhr.status + ' (' + status + ')</div>');
                console.error("Erro no AJAX de Navegação:", status, error, xhr.responseText);
            }
        });
    }

    // NOVA FUNÇÃO: LÓGICA CENTRALIZADA DE CARREGAMENTO DE PÁGINAS DE CLIENTES
    // Esta função será usada tanto pelo "Listar Todos" quanto pelos botões de paginação.
    function carregarPaginaClientes(pagina = 1, termoBusca = {}, listarTodos = false) {
        const limite = 10; // Definido como 10
        const offset = (pagina - 1) * limite;
        
        var resultadoArea = $('#resultado-busca-rapida'); 

        resultadoArea.html('<div class="text-center mt-3"><i class="fas fa-spinner fa-spin me-2 text-info"></i> Carregando resultados...</div>');

        // Prepara os dados a serem enviados
        let dados = { 
            pagina_atual: pagina, // Número da página
            limite: limite,       // Limite por página
            offset: offset,       // Deslocamento
            listar_todos: listarTodos ? 'true' : 'false',
            // Adiciona termos de busca se existirem
            ...termoBusca 
        };

        // NOTA: Para simulação, estamos usando um arquivo que faria a consulta paginada.
        // O cliente_buscar_rapido.php real deve ser modificado para aceitar estes parâmetros.
        $.ajax({
            type: 'GET', 
            url: 'clientes_buscar_rapido.php', // Use o seu arquivo de busca real
            data: dados,
            dataType: 'html', 
            success: function(data) {
                resultadoArea.html(data); 
            },
            error: function(xhr, status, error) {
                resultadoArea.html('<div class="alert alert-danger mt-3">Erro ao carregar a página de clientes. Status: ' + xhr.status + '</div>');
                console.error("Erro no AJAX de Paginação:", xhr.responseText);
            }
        });
    }

    $(document).ready(function() {
        
        // ... (Seu código para urlParams, paginaInicial, e Navegação por Menu) ... (MANTIDO)
        const urlParams = new URLSearchParams(window.location.search);
        const paginaInicial = urlParams.get('p') || 'home.php';

        $(document).on('click', '.item-menu-ajax', function(e) {
            e.preventDefault(); 
            var $this = $(this);
            var pagina = $this.data('pagina');
            if (pagina) {
                carregarConteudo(pagina);
            }
        });

        // ... (Seu código para LÓGICA DE ENVIO DE FORMULÁRIOS) ... (MANTIDO)

        // ... (Seu código para LÓGICA DE BUSCA RÁPIDA DE CLIENTES) ... 
        // MODIFICAÇÃO: A busca rápida agora usará a nova função de paginação, iniciando na página 1
        $(document).on('submit', '#form-busca-cliente-rapida', function(e) {
            e.preventDefault(); 
            
            var form = $(this);
            var dados = form.serializeArray().reduce(function(obj, item) {
                obj[item.name] = item.value;
                return obj;
            }, {});

            // Remove o listar_todos se estiver presente nos dados do formulário de busca normal
            delete dados['listar_todos']; 

            // Chama a função de carregamento da página 1 com os termos de busca
            carregarPaginaClientes(1, dados, false);
        });


        // ... (Seu código para MONITORA O BOTÃO VOLTAR/AVANÇAR) ... (MANTIDO)
        window.onpopstate = function(event) {
            if (event.state && event.state.pagina) {
                carregarConteudo(event.state.pagina, false); 
            } else {
                carregarConteudo(paginaInicial, false);
            }
        };

        // LÓGICA DE LISTAR TODOS OS CLIENTES (MODIFICADA para chamar a página 1)
        $(document).on('click', '#btn-listar-todos-clientes', function(e) {
            e.preventDefault(); 
            
            // Limpa o formulário de busca
            $('#form-busca-cliente-rapida')[0].reset();
            
            // Chama a função de carregamento para listar TODOS a partir da página 1
            carregarPaginaClientes(1, {}, true); 
        });

        // NOVA LÓGICA: LIDA COM OS BOTÕES DE PAGINAÇÃO (1, 2, 3... e Próximos 10)
        $(document).on('click', '.btn-pagina-cliente', function(e) {
            e.preventDefault(); 
            
            var paginaDesejada = $(this).data('pagina');
            var listarTodos = $(this).data('listar-todos') === true; // true ou false
            
            // Reutiliza os termos de busca atuais do formulário, ou usa {} se for 'listar todos'
            var termoBusca = {};
            if (!listarTodos) {
                 termoBusca = $('#form-busca-cliente-rapida').serializeArray().reduce(function(obj, item) {
                    obj[item.name] = item.value;
                    return obj;
                }, {});
            }

            carregarPaginaClientes(paginaDesejada, termoBusca, listarTodos);
        });
        
        // ... (Seu código para LÓGICA DE ESCONDER CLIENTES) ... (MANTIDO)
        $(document).on('click', '#btn-esconder-clientes', function(e) {
            e.preventDefault(); 
            var resultadoArea = $('#resultado-busca-rapida'); 
            resultadoArea.html(`
                <div class="alert alert-info">
                    Preencha um ou mais campos. A busca retornará clientes que correspondam a pelo menos uma das informações fornecidas (ID, CPF ou Nome).
                </div>
            `);
        });

        // CARREGA O CONTEÚDO INICIAL
        carregarConteudo(paginaInicial, false); 
    });
</script>