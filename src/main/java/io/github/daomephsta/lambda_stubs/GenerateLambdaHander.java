package io.github.daomephsta.lambda_stubs;

import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.LambdaExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ImportRewrite;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.TextEditGroup;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;

import com.google.common.collect.Lists;

public class GenerateLambdaHander extends AbstractHandler
{
    private static final ASTParser AST_PARSER = ASTParser.newParser(AST.JLS15);

    @Override
    public Object execute(ExecutionEvent event) throws ExecutionException
    {
        ITextEditor editor = getActiveTextEditor();
        if (editor == null)
            return null;
        IFile open = Adapters.adapt(editor.getEditorInput(), IFile.class);
        ICompilationUnit compilationUnit = JavaCore.createCompilationUnitFrom(open);
        if (compilationUnit == null)
            return null;
        IJavaProject javaProject = compilationUnit.getJavaProject();
        if (javaProject == null)
            return null;
        ISelection selection = editor.getSelectionProvider().getSelection();
        if (!(selection instanceof ITextSelection))
            return null;
        generateLambda(compilationUnit, javaProject, editor, (ITextSelection) selection);
        return null;
    }

    private static ITextEditor getActiveTextEditor()
    {
        IEditorPart editor = PlatformUI.getWorkbench().getActiveWorkbenchWindow()
            .getActivePage().getActiveEditor();
        if (!(editor instanceof ITextEditor))
            return null;
        ITextEditor textEditor = (ITextEditor) editor;
        return textEditor;
    }

    private void generateLambda(ICompilationUnit compilationUnit, IJavaProject project, ITextEditor editor, ITextSelection selection)
    {
        AST_PARSER.setSource(compilationUnit);
        AST_PARSER.setProject(project);
        AST_PARSER.setResolveBindings(true);
        AST_PARSER.setStatementsRecovery(true);

        CompilationUnit astRoot = (CompilationUnit) AST_PARSER.createAST(null);
        astRoot.recordModifications();
        NodeFinder nodeFinder = new NodeFinder(astRoot, selection.getOffset(), 0);
        ASTNode node = nodeFinder.getCoveringNode();
        if (node.getParent() instanceof MethodInvocation)
        {
            MethodInvocation methodInvocation = (MethodInvocation) node.getParent();
            int parameterIndex = ASTNodes.getListProperty(methodInvocation, MethodInvocation.ARGUMENTS_PROPERTY).indexOf(node);
            ITypeBinding lambdaType = methodInvocation.resolveMethodBinding().getParameterTypes()[parameterIndex];
            if (lambdaType.getFunctionalInterfaceMethod() == null)
                return;
            ASTRewrite astRewriter = ASTRewrite.create(astRoot.getAST());
            displayLambdaPopup(editor, astRoot, lambdaType, (lambda, editGroup) -> astRewriter.replace(node, lambda, editGroup), astRewriter::rewriteAST);
        }
        else if (node instanceof VariableDeclarationFragment)
            processFragment(editor, astRoot, (VariableDeclarationFragment) node);
        else if (node.getParent() instanceof VariableDeclarationFragment)
            processFragment(editor, astRoot, (VariableDeclarationFragment) node.getParent());
        else if (node instanceof VariableDeclarationStatement)
        {
            Lists.reverse(ASTNodes.<VariableDeclarationFragment>getListProperty(node, VariableDeclarationStatement.FRAGMENTS_PROPERTY)).stream()
                .filter(fragment -> fragment.getStartPosition() <= selection.getOffset())
                .findFirst()
                .ifPresent(fragment -> processFragment(editor, astRoot, fragment));
        }
    }

    private void processFragment(ITextEditor editor, CompilationUnit astRoot, VariableDeclarationFragment variable)
    {
        ITypeBinding lambdaType = ((VariableDeclarationStatement) variable.getParent()).getType().resolveBinding();

        if (lambdaType.getFunctionalInterfaceMethod() == null)
            return;
        displayLambdaPopup(editor, astRoot, lambdaType, (lambda, editGroup) -> variable.setInitializer(lambda), astRoot::rewrite);
    }

    private void displayLambdaPopup(ITextEditor editor, CompilationUnit astRoot, ITypeBinding lambdaType,
        BiConsumer<LambdaExpression, TextEditGroup> lambdaInserter,
        BiFunction<IDocument, Map<String, String>, TextEdit> astRewriter)
    {
        Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
        Menu pop = new Menu(shell, SWT.POP_UP);
        try
        {
            for (var spec : LambdaSpecification.computeValidSpecifications(lambdaType))
            {
                MenuItem specItem = new MenuItem(pop, SWT.PUSH);
                specItem.setText(spec.describe());
                specItem.addSelectionListener(SelectionListener.widgetSelectedAdapter(event ->
                {
                    try
                    {
                        ImportRewrite importRewriter = ImportRewrite.create(astRoot, true);
                        TextEditGroup editGroup = new TextEditGroup(Messages.text_edit_group_generate_lambda);
                        LambdaExpression lambda = spec.create(astRoot.getAST(), importRewriter);
                        lambdaInserter.accept(lambda, editGroup);
                        IDocument document = editor.getDocumentProvider().getDocument(editor.getEditorInput());
                        TextEdit importEdits = importRewriter.rewriteImports(null),
                                 astEdits = astRewriter.apply(document, null);
                        editGroup.addTextEdit(astEdits);
                        editGroup.addTextEdit(importEdits);
                        importEdits.apply(document);
                        astEdits.apply(document);
                    }
                    catch (CoreException | MalformedTreeException | BadLocationException e)
                    {
                        displayError(e);
                    }
                }));
            }
        }
        catch (JavaModelException e)
        {
            displayError(e);
        }
        pop.setLocation(Display.getCurrent().getCursorLocation());
        pop.setVisible(true);
    }

    private void displayError(Exception e)
    {
        IStatus status = new Status(IStatus.ERROR, LambdaStubs.PLUGIN_ID, Messages.error_applying_proposal, e);
        ErrorDialog.openError(null, Messages.error_dialog_title, Messages.error_applying_proposal, status);
    }
}
