package io.github.daomephsta.lambda_stubs;

import java.util.List;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;

public class ASTNodes
{
    @SuppressWarnings("unchecked")
    static <T> List<T> getListProperty(ASTNode node, ChildListPropertyDescriptor property)
    {
        return (List<T>) node.getStructuralProperty(property);
    }
}
