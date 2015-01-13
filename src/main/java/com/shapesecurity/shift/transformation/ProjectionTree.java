/*
 * Copyright 2014 Shape Security, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.shapesecurity.shift.transformation;

import com.shapesecurity.functional.Effect;
import com.shapesecurity.functional.F;
import com.shapesecurity.functional.F2;
import com.shapesecurity.functional.Pair;
import com.shapesecurity.functional.data.List;
import com.shapesecurity.functional.data.Maybe;
import com.shapesecurity.functional.data.NonEmptyList;
import com.shapesecurity.shift.ast.Node;
import com.shapesecurity.shift.ast.ReplacementChild;
import com.shapesecurity.shift.ast.Script;
import com.shapesecurity.shift.path.Branch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ProjectionTree<E> {
  @NotNull
  protected final List<Branch> path;
  public final int length;
  public final int pathLength;

  private static final ProjectionTree<Object> NIL = new Nil<>();

  private ProjectionTree(@NotNull List<Branch> path, int length) {
    this.path = path;
    this.pathLength = path.length;
    this.length = length;
  }

  public static <A> ProjectionTree<A> create(@NotNull A node, @NotNull List<Branch> path) {
    return new NonEmpty<>(Maybe.just(node), path);
  }

  @SuppressWarnings("unchecked")
  public static <T> ProjectionTree<T> nil() {
    return (ProjectionTree<T>) NIL;
  }

  @NotNull
  public abstract <A> ProjectionTree<A> map(@NotNull F<E, A> f);

  public abstract boolean exists(@NotNull F<E, Boolean> f);

  public abstract boolean isEmpty();

  public abstract void foreach(@NotNull Effect<E> f);

  @NotNull
  public abstract Maybe<E> find(@NotNull F<E, Boolean> f);

  @NotNull
  public abstract <R> Maybe<R> findMap(@NotNull F<E, Maybe<R>> f);

  @NotNull
  public final NonEmpty<E> add(@NotNull E node, @NotNull List<Branch> path) {
    return add(node, path, (a, b) -> b);
  }

  @NotNull
  public abstract NonEmpty<E> add(@NotNull E node, @NotNull List<Branch> path, @NotNull F2<E, E, E> merger);

  @NotNull
  public final ProjectionTree<E> append(@NotNull ProjectionTree<E> other) {
    return append(other, (a, b) -> b);
  }

  @NotNull
  public abstract ProjectionTree<E> append(@NotNull ProjectionTree<E> other, @NotNull F2<E, E, E> merger);

  @NotNull
  public abstract Maybe<E> get(@NotNull List<Branch> path);

  private static class Wrapper {
    @NotNull
    private final List<Branch> path;

    private final int hashCode;

    private Wrapper(@NotNull List<Branch> path) {
      this.path = path;
      this.hashCode = System.identityHashCode(path);
    }

    @Override
    public int hashCode() {
      return this.hashCode;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this || obj instanceof Wrapper && ((Wrapper) obj).path == this.path;
    }
  }

  @NotNull
  public static <T> ProjectionTree<T> from(@NotNull Map<List<Branch>, T> bundle) {
    if (bundle.size() == 0) {
      return nil();
    }
    int maxLength = 0;
    for (Map.Entry<List<Branch>, T> entry : bundle.entrySet()) {
      int l = entry.getKey().length;
      if (maxLength < l) {
        maxLength = l;
      }
    }
    @SuppressWarnings("unchecked")
    Map<Wrapper, Pair<Maybe<Branch>, NonEmpty<T>>>[] queue = new HashMap[maxLength + 1];
    for (Map.Entry<List<Branch>, T> entry : bundle.entrySet()) {
      int l = entry.getKey().length;
      if (queue[l] == null) {
        queue[l] = new HashMap<>();
      }
      queue[l].put(
          new Wrapper(entry.getKey()),
          new Pair<>(Maybe.<Branch>nothing(), new NonEmpty<>(Maybe.just(entry.getValue()), entry.getKey())));
    }
    for (int depth = maxLength; depth > 0; depth--) {
      Map<Wrapper, Pair<Maybe<Branch>, NonEmpty<T>>> curr = queue[depth];
      Map<Wrapper, Pair<Maybe<Branch>, NonEmpty<T>>> parent = queue[depth - 1];
      if (parent == null) {
        parent = queue[depth - 1] = new HashMap<>();
      }
      for (Map.Entry<Wrapper, Pair<Maybe<Branch>, NonEmpty<T>>> entry : curr.entrySet()) {
        Pair<Maybe<Branch>, NonEmpty<T>> pair = entry.getValue();
        NonEmpty<T> currTree = pair.b;
        NonEmptyList<Branch> currPath = (NonEmptyList<Branch>) entry.getKey().path;
        Branch currBranch = currPath.head;
        Wrapper tail = new Wrapper(currPath.tail());
        Pair<Maybe<Branch>, NonEmpty<T>> parentPair = parent.get(tail);
        if (parentPair != null) {
          NonEmpty<T> parentTree = parentPair.b;
          HashMap<Branch, NonEmpty<T>> parentChildren = parentTree.children;
          if (parentPair.a.isJust()) {
            parentChildren = new HashMap<>();
            parentChildren.put(parentPair.a.just(), parentTree);
            parentChildren.put(currBranch, currTree);
            parent.put(
                tail,
                new Pair<>(Maybe.<Branch>nothing(),
                    new NonEmpty<>(
                        Maybe.nothing(),
                        parentChildren,
                        tail.path,
                        parentTree.length + currTree.length)));
          } else {
            parentChildren.put(currBranch, currTree);
          }

        } else {
          parent.put(
              tail,
              new Pair<>(Maybe.just(currPath.head), currTree));
        }
      }
      queue[depth] = null;
    }
    Iterator<Pair<Maybe<Branch>, NonEmpty<T>>> it = queue[0].values().iterator();
    assert it.hasNext();
    return it.next().b;
  }

  private static class Nil<T> extends ProjectionTree<T> {

    private Nil() {
      super(List.<Branch>nil(), 0);
    }

    @NotNull
    @Override
    public <A> ProjectionTree<A> map(@NotNull F<T, A> f) {
      return nil();
    }

    @Override
    public boolean isEmpty() {
      return true;
    }

    @Override
    public void foreach(@NotNull Effect<T> f) {
    }

    @Override
    public boolean exists(@NotNull F<T, Boolean> f) {
      return false;
    }

    @NotNull
    @Override
    public Maybe<T> find(@NotNull F<T, Boolean> f) {
      return Maybe.nothing();
    }

    @NotNull
    @Override
    public <R> Maybe<R> findMap(@NotNull F<T, Maybe<R>> f) {
      return Maybe.nothing();
    }

    @NotNull
    @Override
    public NonEmpty<T> add(@NotNull T node, @NotNull List<Branch> path, @NotNull F2<T, T, T> merger) {
      return new NonEmpty<>(Maybe.just(node), path);
    }

    @NotNull
    @Override
    public ProjectionTree<T> append(@NotNull ProjectionTree<T> other, @NotNull F2<T, T, T> merger) {
      return other;
    }

    @NotNull
    @Override
    public Maybe<T> get(@NotNull List<Branch> path) {
      return Maybe.nothing();
    }
  }

  private static class NonEmpty<E> extends ProjectionTree<E> {
    @NotNull
    public final Maybe<E> maybeNode;
    @NotNull
    public final HashMap<Branch, NonEmpty<E>> children; // leaves do not have a children map

    private NonEmpty(@NotNull Maybe<E> maybeNode,
                     @NotNull List<Branch> path) {
      this(maybeNode, new HashMap<>(), path, 1);
    }

    private NonEmpty(@NotNull Maybe<E> maybeNode,
                     @NotNull HashMap<Branch, NonEmpty<E>> children,
                     @NotNull List<Branch> path,
                     int length) {
      super(path, length);
      this.maybeNode = maybeNode;
      this.children = children;
    }

    @NotNull
    @Override
    public <A> NonEmpty<A> map(@NotNull F<E, A> f) {
      HashMap<Branch, NonEmpty<A>> newChildren = new HashMap<>();
      for (Map.Entry<Branch, NonEmpty<E>> ent : children.entrySet()) {
        newChildren.put(ent.getKey(), ent.getValue().map(f));
      }
      return new NonEmpty<>(maybeNode.map(f), newChildren, path, length);
    }

    public boolean exists(@NotNull F<E, Boolean> f) {
      if (maybeNode.isJust()) {
        if (f.apply(maybeNode.just())) {
          return true;
        }
      }

      for (Map.Entry<Branch, NonEmpty<E>> ent : children.entrySet()) {
        if (ent.getValue().exists(f)) {
          return true;
        }
      }

      return false;
    }

    @Override
    public boolean isEmpty() {
      return false;
    }

    @Override
    public void foreach(@NotNull Effect<E> f) {
      if (maybeNode.isJust()) {
        f.apply(maybeNode.just());
      }
      children.values().forEach(v -> v.foreach(f));
    }

    @NotNull
    public <R> Maybe<R> findMap(@NotNull F<E, Maybe<R>> f) {
      if (maybeNode.isJust()) {
        Maybe<R> result = f.apply(maybeNode.just());
        if (result.isJust()) {
          return result;
        }
      }
      for (NonEmpty<E> es : children.values()) {
        Maybe<R> map = es.findMap(f);
        if (map.isJust()) {
          return map;
        }
      }
      return Maybe.nothing();
    }

    @NotNull
    public Maybe<E> find(@NotNull F<E, Boolean> f) {
      if (maybeNode.isJust()) {
        if (f.apply(maybeNode.just())) {
          return maybeNode;
        }
      }

      for (NonEmpty<E> es : children.values()) {
        Maybe<E> map = es.find(f);
        if (map.isJust()) {
          return map;
        }
      }
      return Maybe.nothing();
    }

    @Override
    @NotNull
    public NonEmpty<E> add(@NotNull E node, @NotNull List<Branch> path, @NotNull F2<E, E, E> merger) {
      return append(new NonEmpty<>(Maybe.just(node), path), merger);
    }

    @Override
    @NotNull
    public NonEmpty<E> append(@NotNull ProjectionTree<E> other, @NotNull F2<E, E, E> merger) {
      if (other instanceof Nil) {
        return this;
      }
      return appendNE((NonEmpty<E>) other, merger);
    }

    @NotNull
    @Override
    public Maybe<E> get(@NotNull List<Branch> path) {
      return getPrivate(scan(path, path.length));
    }

    @NotNull
    private Maybe<E> getPrivate(@NotNull NonEmptyList<Branch>[] scan) {
      int myLength = this.pathLength;
      int targetLength = scan.length;
      if (myLength > targetLength) {
        return Maybe.nothing();
      } else if (myLength == targetLength) {
        if (this.maybeNode.isJust() && scan[targetLength - 1].equals(this.path)) {
          return this.maybeNode;
        }
        return Maybe.nothing();
      } else {
        @Nullable
        NonEmpty<E> child = this.children.get(scan[myLength].head);
        if (child == null) {
          return Maybe.nothing();
        }
        return child.getPrivate(scan);
      }
    }

    @NotNull
    private static NonEmptyList<Branch>[] scan(@NotNull List<Branch> path, int length) {
      //noinspection unchecked
      NonEmptyList<Branch>[] result = new NonEmptyList[length];
      for (int i = result.length; i > 0; i--) {
        NonEmptyList<Branch> pathNEL = (NonEmptyList<Branch>) path;
        result[i - 1] = pathNEL;
        path = pathNEL.tail();
      }
      return result;
    }

    @NotNull
    private static Maybe<ReplacementChild> transformerList(@NotNull Node node, @NotNull Branch branch, @NotNull F<Node, Node> f) {
      Maybe<? extends Node> maybe = node.get(branch);
      if (maybe.isJust()) {
        return Maybe.just(new ReplacementChild(branch, f.apply(maybe.just())));
      }
      return Maybe.nothing();
    }

    @NotNull
    private static F<Node, Node> transformer(@NotNull NonEmpty<? extends Node> tree, int upTo) {
      int childrenUpTo = tree.pathLength + 1;


      F<Node, List<ReplacementChild>> getChildren =
          node -> {
            ArrayList<ReplacementChild> result = new ArrayList<>();
            for (Map.Entry<Branch, ? extends NonEmpty<? extends Node>> entry : tree.children.entrySet()) {
              Maybe<ReplacementChild> rc = transformerList(node, entry.getKey(), transformer(entry.getValue(), childrenUpTo));
              if (rc.isJust()) {
                result.add(rc.just());
              }
            }
            return List.from(result);
          };

      F<Node, Node> f = tree.maybeNode.isJust() ?
          node -> tree.maybeNode.just() :
          node -> node.set(getChildren.apply(node));

      List<Branch> path = tree.path;
      int myLength = childrenUpTo - 1;
      while (myLength != upTo) {
        NonEmptyList<Branch> nelPath = (NonEmptyList<Branch>) path;
        F<Node, Node> oldF = f;
        f = node -> node.set(transformerList(node, nelPath.head, oldF).toList());
        path = nelPath.tail();
        myLength--;
      }

      return f;
    }

    private static <A> F2<Maybe<A>, Maybe<A>, Maybe<A>> lift(F2<A, A, A> merger) {
      return (a, b) -> a.flatMap(a0 -> b.map(b0 -> merger.apply(a0, b0)));
    }

    @NotNull
    private NonEmpty<E> appendNE(@NotNull NonEmpty<E> other, F2<E, E, E> merger) {
      List<Branch> myPath = path;
      int myLength = pathLength;
      List<Branch> otherPath = other.path;
      int otherLength = other.pathLength;
      Branch myBranch = null;
      while (myLength > otherLength) {
        NonEmptyList<Branch> myNEL = (NonEmptyList<Branch>) myPath;
        myBranch = myNEL.head;
        myPath = myNEL.tail();
        myLength--;
      }
      Branch otherBranch = null;
      while (otherLength > myLength) {
        NonEmptyList<Branch> otherNEL = (NonEmptyList<Branch>) otherPath;
        otherBranch = otherNEL.head;
        otherPath = otherNEL.tail();
        otherLength--;
      }
      while (myPath.isNotEmpty() && myPath != otherPath) {
        NonEmptyList<Branch> myNEL = (NonEmptyList<Branch>) myPath;
        myBranch = myNEL.head;
        myPath = myNEL.tail();
        myLength--;
        NonEmptyList<Branch> otherNEL = (NonEmptyList<Branch>) otherPath;
        otherBranch = otherNEL.head;
        otherPath = otherNEL.tail();
        otherLength--;
      }
      // next handle all preDefaultCases of both, one, or none of the trees (this, other) are that lca
      if (myBranch == null) {
        if (otherBranch == null) {
          // Merge
          HashMap<Branch, NonEmpty<E>> children = new HashMap<>();
          children.putAll(this.children);
          other.children.entrySet().forEach(e -> {
            Branch a = e.getKey();
            NonEmpty<E> b = e.getValue();
            if (children.containsKey(a)) {
              children.put(a, b.appendNE(children.get(a), merger));
            }
          });
          return new NonEmpty<>(lift(merger).apply(this.maybeNode, other.maybeNode), children, myPath,
              this.length + other.length);
        } else {
          // Other is a child of this
          HashMap<Branch, NonEmpty<E>> children = new HashMap<>();
          children.putAll(this.children);
          NonEmpty<E> myChild = this.children.get(otherBranch);
          if (myChild != null) {
            other = other.appendNE(myChild, merger);
          }
          children.put(otherBranch, other);
          return new NonEmpty<>(this.maybeNode, children, myPath, this.length + other.length);
        }
      } else if (otherBranch == null) {
        // Other is a child of this
        HashMap<Branch, NonEmpty<E>> children = new HashMap<>();
        children.putAll(other.children);
        NonEmpty<E> otherChild = other.children.get(myBranch);
        NonEmpty<E> t = this;
        if (otherChild != null) {
          t = t.appendNE(otherChild, merger);
        }
        children.put(myBranch, t);
        return new NonEmpty<>(other.maybeNode, children, otherPath, this.length + other.length);
      } else {
        HashMap<Branch, NonEmpty<E>> children = new HashMap<>();
        children.put(myBranch, this);
        children.put(otherBranch, other);
        return new NonEmpty<>(
            Maybe.<E>nothing(),
            children,
            myPath,
            this.length + other.length);
      }
    }
  }


  @SuppressWarnings("unchecked")
  @NotNull
  public static F<Node, Node> transformer(@NotNull ProjectionTree<? extends Node> tree) {
    if (tree instanceof NonEmpty) {
      return NonEmpty.transformer((NonEmpty<? extends Node>) tree, 0);
    } else {
      return F.id();
    }
  }

  @NotNull
  public static Script transform(@NotNull ProjectionTree<? extends Node> tree, @NotNull Script program) {
    if (tree instanceof NonEmpty) {
      @SuppressWarnings("unchecked")
      NonEmpty<? extends Node> specTree = (NonEmpty<? extends Node>) tree;
      return (Script) NonEmpty.transformer(specTree, 0).apply(program);
    }
    return program;
  }
}
