# Versões e validação

Estado atual: **0.1.0-SNAPSHOT**. O número do manifesto não comprova publicação.
Fonte de versão: `pom.xml`.

## Validar

```text
mvn -B verify
```

O CI mock verifica o baseline. Configure OPENAI_API_KEY/ANTHROPIC_API_KEY como secrets e OPENAI_MODEL/ANTHROPIC_MODEL como variables para execuções reais opcionais. Sem chave ou modelo, o provider é pulado com aviso no resumo. Resultados reais podem variar mesmo com temperatura zero. Para publicar o relatório, habilite GitHub Pages com origem GitHub Actions.

## Preparar uma release

1. Revise o diff e confirme que o commit contém apenas as mudanças previstas.
2. Atualize a versão nos manifestos aplicáveis e as notas de mudanças. Remova
   `-SNAPSHOT` somente ao preparar uma versão publicável. Para versões de teste,
   use um identificador de pré-release e marque essa condição na release.
3. Execute os checks acima e os testes de integração necessários. Registre
   limitações e plataformas não verificadas; não anuncie cobertura não executada.
4. Crie a tag `vMAJOR.MINOR.PATCH` no commit validado e associe os artefatos a ele.
   Tags já publicadas são imutáveis. Não crie versões retroativas só para preencher histórico.
5. Nas notas, informe mudanças, compatibilidade, instalação, migração e limitações.
   Publique somente depois de revisar o pacote e a documentação da mesma versão.

Correções compatíveis incrementam patch; recursos compatíveis incrementam minor;
quebras de contrato exigem revisão de major. Durante 0.x, declare explicitamente
mudanças incompatíveis e a política de suporte. Nem toda alteração de documentação
exige uma nova release de produto.
