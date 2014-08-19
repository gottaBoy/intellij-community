// This is a generated file. Not intended for manual editing.
package com.intellij.json.psi.impl;

import java.util.List;
import org.jetbrains.annotations.*;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import static com.intellij.json.JsonElementTypes.*;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.json.psi.*;

public class JsonPropertyNameImpl extends ASTWrapperPsiElement implements JsonPropertyName {

  public JsonPropertyNameImpl(ASTNode node) {
    super(node);
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JsonVisitor) ((JsonVisitor)visitor).visitPropertyName(this);
    else super.accept(visitor);
  }

  @Override
  @NotNull
  public JsonStringLiteral getStringLiteral() {
    return findNotNullChildByClass(JsonStringLiteral.class);
  }

}
