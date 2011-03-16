// Copyright (c) 2009 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package org.chromium.debug.core.model;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.chromium.sdk.ExceptionData;
import org.chromium.sdk.JsArray;
import org.chromium.sdk.JsFunction;
import org.chromium.sdk.JsObject;
import org.chromium.sdk.JsScope;
import org.chromium.sdk.JsValue;
import org.chromium.sdk.JsVariable;
import org.chromium.sdk.SyncCallback;
import org.chromium.sdk.JsValue.ReloadBiggerCallback;
import org.chromium.sdk.JsValue.Type;
import org.chromium.sdk.JsVariable.SetValueCallback;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.model.IVariable;
import org.eclipse.debug.ui.actions.IWatchExpressionFactoryAdapter;

/**
 * An IVariable implementation over a JsVariable instance. This is class is a base implementation,
 * and it contains several concrete implementations as nested classes.
 */
public abstract class Variable extends DebugElementImpl implements IVariable {

  /**
   * Represents a real variable -- wraps {@link JsVariable}.
   * TODO: consider hiding this public class behind a static factory method.
   */
  public static class Real extends Variable {
    private final JsVariable variable;

    /**
     * Specifies whether this variable is internal property (__proto__ etc).
     * TODO(peter.rybin): use it in UI.
     */
    private final boolean isInternalProperty;

    public Real(EvaluateContext evaluateContext, JsVariable variable,
        boolean isInternalProperty) {
      super(evaluateContext);
      this.variable = variable;
      this.isInternalProperty = isInternalProperty;
    }

    public String getName() {
      return variable.getName();
    }

    public String getReferenceTypeName() throws DebugException {
      return variable.getValue().getType().toString();
    }

    protected Value createValue() {
      JsValue value = variable.isReadable()
          ? variable.getValue()
          : null;
      if (value == null) {
        return null;
      }
      return Value.create(getEvaluateContext(), value);
    }

    @Override
    protected String createWatchExpression() {
      String expression = variable.getFullyQualifiedName();
      if (expression == null) {
        expression = variable.getName();
      }
      return expression;
    }
  }

  /**
   * Represents a scope as a variable, serves for grouping real variables in UI view.
   * TODO: consider hiding this public class behind a static factory method.
   */
  public static class ScopeWrapper extends Variable {
    private final JsScope jsScope;

    public ScopeWrapper(EvaluateContext evaluateContext, JsScope scope) {
      super(evaluateContext);
      this.jsScope = scope;
    }

    @Override
    public String getName() {
      return "<" + jsScope.getType() + ">";
    }

    @Override
    public String getReferenceTypeName() throws DebugException {
      return "<scope>";
    }

    @Override
    protected Value createValue() {
      JsValue scopeValue = new ScopeObjectVariable();
      return Value.create(getEvaluateContext(), scopeValue);
    }

    @Override
    protected String createWatchExpression() {
      return null;
    }

    /**
     * Wraps JsScope as an object value with properties representing scope variables.
     */
    class ScopeObjectVariable implements JsObject {
      public JsArray asArray() {
        return null;
      }
      public JsFunction asFunction() {
        return null;
      }
      public String getClassName() {
        return "#Scope";
      }
      public Collection<? extends JsVariable> getProperties() {
        return jsScope.getVariables();
      }
      public Collection<? extends JsVariable> getInternalProperties() {
        return Collections.emptyList();
      }
      public JsVariable getProperty(String name) {
        for (JsVariable var : getProperties()) {
          if (var.getName().equals(name)) {
            return var;
          }
        }
        return null;
      }
      public JsObject asObject() {
        return this;
      }
      public Type getType() {
        return Type.TYPE_OBJECT;
      }
      public String getValueString() {
        return getClassName();
      }
      public String getRefId() {
        return null;
      }
      public boolean isTruncated() {
        return false;
      }
      public void reloadHeavyValue(ReloadBiggerCallback callback,
          SyncCallback syncCallback) {
        if (syncCallback != null) {
          syncCallback.callbackDone(null);
        }
      }
    }
  }

  /**
   * A fake variable that represents an exception about to be thrown. Used in a fake
   * ExceptionStackFrame.
   * TODO: consider hiding this public class behind a static factory method.
   */
  public static class ExceptionHolder extends Variable {
    private final ExceptionData exceptionData;

    public ExceptionHolder(EvaluateContext evaluateContext, ExceptionData exceptionData) {
      super(evaluateContext);
      this.exceptionData = exceptionData;
    }

    @Override
    public String getName() {
      return "<exception>";
    }

    @Override
    public String getReferenceTypeName() throws DebugException {
      return exceptionData.getExceptionValue().getType().toString();
    }

    @Override
    protected Value createValue() {
      return Value.create(getEvaluateContext(), exceptionData.getExceptionValue());
    }

    @Override
    protected String createWatchExpression() {
      return null;
    }
  }


  private final AtomicReference<Value> valueRef = new AtomicReference<Value>(null);
  private final EvaluateContext evaluateContext;

  public Variable(EvaluateContext evaluateContext) {
    super(evaluateContext.getDebugTarget());
    this.evaluateContext = evaluateContext;
  }

  public abstract String getName();

  public abstract String getReferenceTypeName() throws DebugException;

  public Value getValue() {
    Value result = valueRef.get();
    if (result != null) {
      return result;
    }
    // Only set a value if it hasn't be set already (by a concurrent thread).
    valueRef.compareAndSet(null, createValue());
    return valueRef.get();
  }

  protected abstract Value createValue();

  protected EvaluateContext getEvaluateContext() {
    return evaluateContext;
  }

  public boolean hasValueChanged() throws DebugException {
    return false;
  }

  @SuppressWarnings("unchecked")
  @Override
  public Object getAdapter(Class adapter) {
    if (IWatchExpressionFactoryAdapter.class == adapter) {
      return new IWatchExpressionFactoryAdapter() {
        public String createWatchExpression(IVariable variable) throws CoreException {
          Variable castVariable = (Variable) variable;
          return castVariable.createWatchExpression();
        }
      };
    }
    return super.getAdapter(adapter);
  }

  public void setValue(String expression) throws DebugException {
  }

  public void setValue(IValue value) throws DebugException {
  }

  public boolean supportsValueModification() {
    return false; // TODO(apavlov): fix once V8 supports it
  }

  public boolean verifyValue(IValue value) throws DebugException {
    return verifyValue(value.getValueString());
  }

  public boolean verifyValue(String expression) {
    return true;
  }

  public boolean verifyValue(JsValue value) {
    return verifyValue(value.getValueString());
  }

  protected abstract String createWatchExpression();
}
