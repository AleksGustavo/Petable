<?php
// Arquivo: clientes_cadastro.php
// Apenas o conteúdo HTML que será injetado pelo AJAX.
?>

<div class="d-flex justify-content-between align-items-center mb-3">
    <h2><i class="fas fa-user-plus me-2"></i> Cadastrar Novo Cliente</h2>
    
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
        <form id="form-cadastro-cliente" method="POST" action="clientes_processar.php">
            <div class="row g-2 g-compact">
                
                <div class="col-md-4">
                    <label for="nome" class="form-label">Nome *</label>
                    <input type="text" id="nome" name="nome" class="form-control form-control-sm input-letters-only" required>
                </div>
                
                <div class="col-md-4">
                    <label for="sobrenome" class="form-label">Sobrenome *</label>
                    <input type="text" id="sobrenome" name="sobrenome" class="form-control form-control-sm input-letters-only" required>
                </div>
                
                <div class="col-md-4">
                    <label for="cpf" class="form-label">CPF *</label>
                    <input type="text" id="cpf" name="cpf" class="form-control form-control-sm mask-cpf input-numbers-only" required maxlength="14" placeholder="000.000.000-00">
                    <div id="cpf-feedback" class="invalid-feedback">
                        CPF inválido.
                    </div>
                    <div id="cpf-duplicidade-feedback" class="invalid-feedback" style="display: none;">
                        Este CPF já está cadastrado.
                    </div>
                </div>
                
                <div class="col-md-4">
                    <label for="celular" class="form-label">Celular *</label>
                    <input type="text" id="celular" name="celular" class="form-control form-control-sm mask-celular input-numbers-only" required maxlength="15" placeholder="(00) 00000-0000">
                    <div id="celular-duplicidade-feedback" class="invalid-feedback" style="display: none;">
                        Este telefone já está cadastrado.
                    </div>
                </div>

                <hr class="mt-2">
                <h5 class="mb-2">Endereço</h5>

                <div class="col-md-3">
                    <label for="cep" class="form-label">CEP *</label>
                    <input type="text" id="cep" name="cep" class="form-control form-control-sm mask-cep input-numbers-only" required maxlength="9" placeholder="00000-000">
                    <div id="cep-feedback" class="invalid-feedback">
                        CEP não encontrado.
                    </div>
                </div>
                
                <div class="col-md-2">
                    <label for="uf" class="form-label">Estado (UF) *</label>
                    <input type="text" id="uf" name="uf" class="form-control form-control-sm" required maxlength="2" readonly>
                </div>

                <div class="col-md-4">
                    <label for="rua" class="form-label">Rua *</label>
                    <input type="text" id="rua" name="rua" class="form-control form-control-sm" required>
                </div>
                
                <div class="col-md-3">
                    <label for="numero" class="form-label">Número *</label>
                    <input type="text" id="numero" name="numero" class="form-control form-control-sm input-numbers-only" required>
                </div>
                
                <div class="col-md-6">
                    <label for="bairro" class="form-label">Bairro</label>
                    <input type="text" id="bairro" name="bairro" class="form-control form-control-sm input-letters-only">
                </div>
                
                <div class="col-md-6">
                    <label for="complemento" class="form-label">Complemento</label>
                    <input type="text" id="complemento" name="complemento" class="form-control form-control-sm">
                </div>

                <hr class="mt-2">
                <h5 class="mb-2">Outros Dados</h5>

                <div class="col-md-4">
                    <label for="data_nascimento" class="form-label">Data de Nascimento</label>
                    <input type="date" id="data_nascimento" name="data_nascimento" class="form-control form-control-sm">
                </div>
                <div class="col-md-4">
                    <label for="sexo" class="form-label">Sexo</label>
                    <select id="sexo" name="sexo" class="form-select form-select-sm">
                        <option value="">Selecione...</option>
                        <option value="M">Masculino</option>
                        <option value="F">Feminino</option>
                        <option value="Outro">Outro</option>
                    </select>
                </div>
                
                <div class="col-12 mt-3">
                    <button type="submit" class="btn btn-success btn-sm" id="btn-cadastrar-cliente">
                        <i class="fas fa-save me-2"></i> Cadastrar Cliente
                    </button>
                </div>
            </div>
        </form>
    </div>
</div>

<script>
    document.addEventListener('DOMContentLoaded', function() {
        const nomeInput = document.getElementById('nome');
        const cepInput = document.getElementById('cep');
        const ruaInput = document.getElementById('rua');
        const bairroInput = document.getElementById('bairro');
        const ufInput = document.getElementById('uf');
        const numeroInput = document.getElementById('numero');
        const celularInput = document.getElementById('celular');
        const cpfInput = document.getElementById('cpf');
        
        const cpfFeedback = document.getElementById('cpf-feedback');
        const cpfDuplicidadeFeedback = document.getElementById('cpf-duplicidade-feedback');
        const celularDuplicidadeFeedback = document.getElementById('celular-duplicidade-feedback');
        const cepFeedback = document.getElementById('cep-feedback');
        const form = document.getElementById('form-cadastro-cliente');
        const btnCadastrar = document.getElementById('btn-cadastrar-cliente');
        
        // Estado de validação
        let isCpfDuplicated = false;
        let isCelularDuplicated = false;

        // --- 1. Validação de CPF (Algoritmo de Luhn) ---
        
        function validarCPF(cpf) {
            cpf = cpf.replace(/[^\d]/g, ''); 
            if (cpf.length !== 11 || /^(\d)\1{10}$/.test(cpf)) return false;

            let soma = 0;
            let resto;

            for (let i = 1; i <= 9; i++) {
                soma += parseInt(cpf.substring(i - 1, i)) * (11 - i);
            }
            resto = (soma * 10) % 11;
            if ((resto === 10) || (resto === 11)) resto = 0;
            if (resto !== parseInt(cpf.substring(9, 10))) return false;

            soma = 0;
            for (let i = 1; i <= 10; i++) {
                soma += parseInt(cpf.substring(i - 1, i)) * (12 - i);
            }
            resto = (soma * 10) % 11;
            if ((resto === 10) || (resto === 11)) resto = 0;
            if (resto !== parseInt(cpf.substring(10, 11))) return false;

            return true;
        }
        
        // NOVO: Função para verificar duplicidade de CPF e Celular
        async function checkDuplicity(field, value) {
            // value já deve estar limpo (somente números)
            if (value.length === 0) return false;

            const endpoint = 'clientes_validar_duplicidade.php'; // PONTO DE ATENÇÃO: Arquivo PHP necessário!
            const formData = new FormData();
            formData.append('campo', field);
            formData.append('valor', value);

            try {
                const response = await fetch(endpoint, {
                    method: 'POST',
                    body: formData
                });
                const data = await response.json();
                
                return data.existe_duplicidade; // Espera true se for duplicado
            } catch (error) {
                console.error(`Erro ao verificar duplicidade de ${field}:`, error);
                // Em caso de erro, assumimos que não há duplicidade para não bloquear o usuário.
                return false; 
            }
        }
        
        async function checkCpfValidity() {
            const cpfValue = cpfInput.value;
            const cpfClean = cpfValue.replace(/\D/g, '');
            const isCpfValid = validarCPF(cpfValue);
            
            cpfInput.classList.remove('is-valid', 'is-invalid');
            cpfFeedback.style.display = 'none';
            cpfDuplicidadeFeedback.style.display = 'none';
            isCpfDuplicated = false;

            if (cpfClean.length === 11) {
                if (!isCpfValid) {
                    cpfInput.classList.add('is-invalid');
                    cpfFeedback.style.display = 'block';
                    return false;
                }
                
                // 1. Verificar Duplicidade
                cpfInput.disabled = true;
                const isDuplicated = await checkDuplicity('cpf', cpfClean);
                cpfInput.disabled = false;
                
                if (isDuplicated) {
                    cpfInput.classList.add('is-invalid');
                    cpfDuplicidadeFeedback.style.display = 'block';
                    isCpfDuplicated = true;
                    return false;
                }
                
                // 2. Passou nas validações
                cpfInput.classList.add('is-valid');
                celularInput.focus();
                return true;

            } else {
                 return false;
            }
        }
        
        async function checkCelularValidity() {
            const celularValue = celularInput.value;
            const celularClean = celularValue.replace(/\D/g, '');
            
            celularInput.classList.remove('is-valid', 'is-invalid');
            celularDuplicidadeFeedback.style.display = 'none';
            isCelularDuplicated = false;

            if (celularClean.length === 11) {
                
                // 1. Verificar Duplicidade
                celularInput.disabled = true;
                const isDuplicated = await checkDuplicity('celular', celularClean);
                celularInput.disabled = false;
                
                if (isDuplicated) {
                    celularInput.classList.add('is-invalid');
                    celularDuplicidadeFeedback.style.display = 'block';
                    isCelularDuplicated = true;
                    return false;
                }
                
                // 2. Passou na validação
                celularInput.classList.add('is-valid');
                cepInput.focus();
                return true;
            } else {
                return false;
            }
        }


        cpfInput.addEventListener('blur', checkCpfValidity);
        celularInput.addEventListener('blur', checkCelularValidity);


        // --- 2. API de Busca de CEP (ViaCEP) ---

        function clearAddressFields() {
            ruaInput.value = "";
            bairroInput.value = "";
            ufInput.value = "";
            ruaInput.readOnly = false;
            bairroInput.readOnly = false;
            ufInput.readOnly = false;
        }

        function fillAddressFields(data) {
            ruaInput.value = data.logradouro || '';
            bairroInput.value = data.bairro || '';
            ufInput.value = data.uf || '';
            
            ruaInput.readOnly = !!data.logradouro;
            bairroInput.readOnly = !!data.bairro;
            ufInput.readOnly = !!data.uf;
            
            if (data.logradouro) {
                numeroInput.focus();
            } else {
                ruaInput.focus();
            }
        }

        function searchCep() {
            const cepValue = cepInput.value.replace(/\D/g, '');

            if (cepValue.length !== 8) {
                clearAddressFields();
                cepInput.classList.remove('is-valid', 'is-invalid');
                return;
            }

            cepInput.classList.remove('is-valid', 'is-invalid');
            cepInput.disabled = true;
            clearAddressFields();

            fetch(`https://viacep.com.br/ws/${cepValue}/json/`)
                .then(response => response.json())
                .then(data => {
                    if (!data.erro) {
                        fillAddressFields(data);
                        cepInput.classList.add('is-valid');
                        cepInput.classList.remove('is-invalid');
                    } else {
                        clearAddressFields();
                        cepInput.classList.add('is-invalid');
                        cepFeedback.textContent = "CEP não encontrado. Digite o endereço manualmente.";
                        ruaInput.focus();
                    }
                })
                .catch(error => {
                    console.error('Erro na API ViaCEP:', error);
                    cepInput.classList.add('is-invalid');
                    cepFeedback.textContent = "Erro ao buscar CEP. Tente novamente.";
                    clearAddressFields();
                })
                .finally(() => {
                    cepInput.disabled = false;
                });
        }

        // CORREÇÃO do Enter: Impede a submissão padrão do formulário ao pressionar Enter no campo CEP.
        cepInput.addEventListener('keyup', function(e) {
            if (e.key === 'Enter' || e.keyCode === 13) {
                e.preventDefault(); 
                
                if (this.value.length === 9) {
                    searchCep();
                }
            } else if (this.value.length === 9) {
                searchCep();
            }
        });
        
        cepInput.addEventListener('blur', searchCep);
        
        // --- 3. Prevenção de envio ---
        
        form.addEventListener('submit', async function(e) {
            e.preventDefault(); // Previne o envio inicial
            
            // Revalida CPF e Celular na submissão
            const isCpfOk = await checkCpfValidity();
            const isCelularOk = await checkCelularValidity();

            // Verifica se há CPF inválido (padrão ou duplicidade)
            if (!isCpfOk || isCpfDuplicated) {
                alert('Por favor, corrija o CPF. Ele é obrigatório, precisa ser válido e não deve ser duplicado.');
                cpfInput.focus();
                return;
            }
            
            // Verifica se há Celular duplicado
            if (isCelularDuplicated) {
                alert('Por favor, corrija o Celular. Ele não pode ser duplicado.');
                celularInput.focus();
                return;
            }
            
            // Se todas as validações de JS passaram, submete o formulário
            // Nota: Se houver outros campos que o navegador possa marcar como inválidos (required),
            // você deve forçar o clique no botão de submit aqui, mas como já deu preventDefault,
            // a maneira mais segura é submeter via JS:
            this.submit();
        });
        
        // --- 4. Máscaras (Ajustadas para não forçar foco) ---

        // Função de máscara CPF (Mantida a correção para não pular campo)
        document.querySelectorAll('.mask-cpf').forEach(input => {
            input.addEventListener('input', function(e) {
                let value = e.target.value.replace(/\D/g, '');
                if (value.length > 9) {
                    value = value.replace(/(\d{3})(\d{3})(\d{3})(\d{2})/, '$1.$2.$3-$4');
                } else if (value.length > 6) {
                    value = value.replace(/(\d{3})(\d{3})(\d{3})/, '$1.$2.$3');
                } else if (value.length > 3) {
                    value = value.replace(/(\d{3})(\d{3})/, '$1.$2');
                } else if (value.length > 0) {
                    value = value.replace(/(\d{3})/, '$1');
                }
                e.target.value = value;
            });
        });

        // Função de máscara Celular 
        document.querySelectorAll('.mask-celular').forEach(input => {
            input.addEventListener('input', function(e) {
                let value = e.target.value.replace(/\D/g, '');
                if (value.length > 10) {
                    value = value.replace(/^(\d{2})(\d{5})(\d{4})/, '($1) $2-$3');
                } else if (value.length > 6) {
                    value = value.replace(/^(\d{2})(\d{4})(\d{0,4})/, '($1) $2-$3');
                } else if (value.length > 2) {
                    value = value.replace(/^(\d{2})(\d{0,5})/, '($1) $2');
                } else if (value.length > 0) {
                    value = value.replace(/^(\d{0,2})/, '($1');
                }
                e.target.value = value;
            });
        });

        // Função de máscara CEP
        document.querySelectorAll('.mask-cep').forEach(input => {
            input.addEventListener('input', function(e) {
                let value = e.target.value.replace(/\D/g, '');
                if (value.length > 5) {
                    value = value.replace(/(\d{5})(\d{1,3})/, '$1-$2');
                }
                e.target.value = value;
            });
        });
        
        nomeInput.focus(); // Foco inicial
    });
</script>